#include <jni.h>
#include <android/log.h>
#include <android/bitmap.h>
#include <android/asset_manager_jni.h>
#include <string>
#include <vector>
#include <cmath>

#include "ncnn/net.h"
#include "ncnn/gpu.h"
#include "rife_ops.h"  // Custom RIFE layers (Warp)

#define LOG_TAG "RIFEProcessor"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Custom layer creator for RIFE Warp layer
static ncnn::Layer* Warp_layer_creator(void*) {
    return new Warp();
}

// RIFE network wrapper
class RIFENet {
public:
    ncnn::Net flownet;
    int gpu_device = -1;
    bool tta_mode = false;

    RIFENet(int gpu_id = -1, bool tta = false) : gpu_device(gpu_id), tta_mode(tta) {
        ncnn::create_gpu_instance();
        if (gpu_device >= 0 && ncnn::get_gpu_count() > 0) {
            flownet.opt.use_vulkan_compute = true;
            // CRITICAL: Set Vulkan device on the network!
            flownet.set_vulkan_device(gpu_device);
            LOGI("Vulkan device %d set on flownet", gpu_device);
        }
        flownet.opt.use_fp16_packed = true;
        flownet.opt.use_fp16_storage = true;
        flownet.opt.use_fp16_arithmetic = false;
        flownet.opt.use_int8_storage = true;

        // Register custom RIFE layers
        flownet.register_custom_layer("rife.Warp", Warp_layer_creator);
        LOGI("Custom RIFE Warp layer registered successfully");
    }

    ~RIFENet() {
        flownet.clear();
        ncnn::destroy_gpu_instance();
    }

    int load(AAssetManager* mgr, const char* model_path) {
        LOGI("Loading RIFE model from: %s", model_path);

        // Build full paths
        std::string param_path = std::string(model_path) + "/flownet.param";
        std::string bin_path = std::string(model_path) + "/flownet.bin";

        LOGI("Attempting to load param: %s", param_path.c_str());

        // Load param file from assets
        int ret = flownet.load_param(mgr, param_path.c_str());
        if (ret != 0) {
            LOGE("Failed to load param: %s (error code: %d)", param_path.c_str(), ret);

            // Try alternative path without subfolder
            LOGI("Trying alternative: flownet.param");
            ret = flownet.load_param(mgr, "flownet.param");
            if (ret != 0) {
                LOGE("Failed alternative param load too!");
                return ret;
            }
            LOGI("Alternative param path worked!");
        } else {
            LOGI("Param loaded successfully from: %s", param_path.c_str());
        }

        LOGI("Attempting to load model: %s", bin_path.c_str());

        // Load model file from assets
        ret = flownet.load_model(mgr, bin_path.c_str());
        if (ret != 0) {
            LOGE("Failed to load model: %s (error code: %d)", bin_path.c_str(), ret);

            // Try alternative path
            LOGI("Trying alternative: flownet.bin");
            ret = flownet.load_model(mgr, "flownet.bin");
            if (ret != 0) {
                LOGE("Failed alternative model load too!");
                return ret;
            }
            LOGI("Alternative model path worked!");
        } else {
            LOGI("Model loaded successfully from: %s", bin_path.c_str());
        }

        LOGI("✅ RIFE flownet loaded successfully!");
        return 0;
    }

    int process(const ncnn::Mat& in0, const ncnn::Mat& in1, float timestep, ncnn::Mat& out) {
        // Ensure input dimensions are multiples of 32 for RIFE model compatibility
        int w = in0.w;
        int h = in0.h;
        int c = in0.c;
        
        LOGI("Processing frames with timestep: %.3f, dimensions: %dx%d", timestep, w, h);
        
        // Validate timestep is in valid range [0, 1]
        if (timestep < 0.0f || timestep > 1.0f) {
            LOGE("Invalid timestep value: %.3f, must be between 0.0 and 1.0", timestep);
            timestep = std::max(0.0f, std::min(1.0f, timestep)); // Clamp to valid range
        }

        // Create input tensors (make copies for normalization)
        ncnn::Mat in0_resized, in1_resized;
        
        // If dimensions are not multiples of 32, resize them
        if (w % 32 != 0 || h % 32 != 0) {
            int new_w = ((w + 31) / 32) * 32;
            int new_h = ((h + 31) / 32) * 32;
            
            LOGI("Resizing input from %dx%d to %dx%d for RIFE compatibility", w, h, new_w, new_h);
            
            ncnn::resize_bilinear(in0, in0_resized, new_w, new_h);
            ncnn::resize_bilinear(in1, in1_resized, new_w, new_h);
        } else {
            in0_resized = in0.clone();
            in1_resized = in1.clone();
        }

        // Normalize to [-1, 1] - RIFE expects this range
        const float norm_vals[3] = {1.f / 127.5f, 1.f / 127.5f, 1.f / 127.5f};
        const float mean_vals[3] = {127.5f, 127.5f, 127.5f};
        in0_resized.substract_mean_normalize(mean_vals, norm_vals);
        in1_resized.substract_mean_normalize(mean_vals, norm_vals);

        #if NCNN_VULKAN
        // Use Vulkan GPU path if available
        if (flownet.opt.use_vulkan_compute) {
            LOGI("Using Vulkan GPU path with proper command synchronization...");

            // Get Vulkan device from the network (it's already configured)
            const ncnn::VulkanDevice* vkdev = flownet.vulkan_device();
            if (!vkdev) {
                LOGE("Failed to get Vulkan device from flownet, falling back to CPU");
                goto cpu_path;
            }

            LOGI("Got Vulkan device from flownet: %p", vkdev);

            // Create allocators for Vulkan operations
            ncnn::VkAllocator* blob_vkallocator = vkdev->acquire_blob_allocator();
            ncnn::VkAllocator* staging_vkallocator = vkdev->acquire_staging_allocator();

            // Create the VkCompute command object first
            ncnn::VkCompute cmd(vkdev);

            // Create extractor and set allocators
            ncnn::Extractor ex = flownet.create_extractor();
            ex.set_blob_vkallocator(blob_vkallocator);
            ex.set_workspace_vkallocator(blob_vkallocator);
            ex.set_staging_vkallocator(staging_vkallocator);

            // Extract output on GPU
            ncnn::VkMat out_gpu;
            LOGI("Extracting output from flownet (GPU)...");
            int ret = ex.extract("out0", out_gpu, cmd);
            if (ret != 0) {
                LOGE("Failed to extract GPU output, ret=%d", ret);
                vkdev->reclaim_blob_allocator(blob_vkallocator);
                vkdev->reclaim_staging_allocator(staging_vkallocator);
                return ret;
            }

            // Download result from GPU to CPU using proper pipeline
            ncnn::Mat out_cpu;
            // Create a temporary CPU mat with the same size as GPU output
            out_cpu.create_like(out_gpu, 0);  // Use default CPU allocator
            
            // Record download command
            cmd.record_download(out_gpu, out_cpu, flownet.opt);
            
            // Submit and wait for download to complete
            cmd.submit_and_wait();

            LOGI("Output downloaded from GPU: %dx%dx%d", out_cpu.w, out_cpu.h, out_cpu.c);

            // Release allocators
            vkdev->reclaim_blob_allocator(blob_vkallocator);
            vkdev->reclaim_staging_allocator(staging_vkallocator);

            // Denormalize
            ncnn::Mat out_denorm = out_cpu.clone();
            for (int c = 0; c < out_denorm.c; c++) {
                float* ptr = out_denorm.channel(c);
                int size = out_denorm.w * out_denorm.h;
                for (int i = 0; i < size; i++) {
                    ptr[i] = (ptr[i] + 1.f) * 127.5f; // Convert from [-1,1] to [0,255]
                }
            }

            // If we resized the input, resize the output back to original size
            ncnn::Mat final_output;
            if (out_denorm.w != w || out_denorm.h != h) {
                LOGI("Resizing output from %dx%d back to original %dx%d", out_denorm.w, out_denorm.h, w, h);
                ncnn::resize_bilinear(out_denorm, final_output, w, h);
            } else {
                final_output = out_denorm;
            }
            
            // Apply basic post-processing to enhance output quality
            // Slight sharpening to counteract any blur from interpolation
            const int fw = final_output.w;
            const int fh = final_output.h;
            const int fc = final_output.c;
            
            if (fw >= 3 && fh >= 3) {
                ncnn::Mat sharpened = final_output.clone();
                
                for (int ch = 0; ch < fc; ch++) {
                    float* ptr = final_output.channel(ch);
                    float* out_ptr = sharpened.channel(ch);
                    
                    for (int y = 1; y < fh-1; y++) {
                        for (int x = 1; x < fw-1; x++) {
                            // Apply sharpening kernel
                            float center = ptr[y * fw + x] * 5.0f;
                            float top = ptr[(y-1) * fw + x] * -1.0f;
                            float bottom = ptr[(y+1) * fw + x] * -1.0f;
                            float left = ptr[y * fw + (x-1)] * -1.0f;
                            float right = ptr[y * fw + (x+1)] * -1.0f;
                            
                            float result = center + top + bottom + left + right;
                            
                            // Clamp to valid range [0, 255]
                            if (result < 0.0f) result = 0.0f;
                            if (result > 255.0f) result = 255.0f;
                            
                            out_ptr[y * fw + x] = result;
                        }
                    }
                }
                
                out = sharpened;
            } else {
                out = final_output;
            }
            
            return 0;
        }
        #endif

        // CPU fallback path
        cpu_path:
        LOGI("Using CPU path...");

        // Create extractor for CPU
        ncnn::Extractor ex = flownet.create_extractor();

        // Set inputs (use exact names from .param file)
        ex.input("in0", in0_resized);
        ex.input("in1", in1_resized);

        // Create timestep tensor
        ncnn::Mat timestep_mat(1);
        timestep_mat[0] = timestep;
        ex.input("in2", timestep_mat);

        // Extract output (use exact name from .param file)
        ncnn::Mat out_cpu;
        LOGI("Extracting output from flownet (CPU)...");
        int ret = ex.extract("out0", out_cpu);
        if (ret != 0) {
            LOGE("Failed to extract output, ret=%d", ret);
            return ret;
        }
        LOGI("Output extracted: %dx%dx%d", out_cpu.w, out_cpu.h, out_cpu.c);

        // Denormalize back to [0, 255] from [-1, 1]
        ncnn::Mat out_denorm = out_cpu.clone();

        // Convert from [-1, 1] to [0, 255]
        for (int c = 0; c < out_denorm.c; c++) {
            float* ptr = out_denorm.channel(c);
            int size = out_denorm.w * out_denorm.h;
            for (int i = 0; i < size; i++) {
                ptr[i] = (ptr[i] + 1.f) * 127.5f; // Convert from [-1,1] to [0,255]
            }
        }

        // If we resized the input, resize the output back to original size
        ncnn::Mat final_output;
        if (out_denorm.w != w || out_denorm.h != h) {
            LOGI("Resizing output from %dx%d back to original %dx%d", out_denorm.w, out_denorm.h, w, h);
            ncnn::resize_bilinear(out_denorm, final_output, w, h);
        } else {
            final_output = out_denorm;
        }
        
        // Apply basic post-processing to enhance output quality
        // Slight sharpening to counteract any blur from interpolation
        const int fw = final_output.w;
        const int fh = final_output.h;
        const int fc = final_output.c;
        
        if (fw >= 3 && fh >= 3) {
            ncnn::Mat sharpened = final_output.clone();
            
            for (int ch = 0; ch < fc; ch++) {
                float* ptr = final_output.channel(ch);
                float* out_ptr = sharpened.channel(ch);
                
                for (int y = 1; y < fh-1; y++) {
                    for (int x = 1; x < fw-1; x++) {
                        // Apply sharpening kernel
                        float center = ptr[y * fw + x] * 5.0f;
                        float top = ptr[(y-1) * fw + x] * -1.0f;
                        float bottom = ptr[(y+1) * fw + x] * -1.0f;
                        float left = ptr[y * fw + (x-1)] * -1.0f;
                        float right = ptr[y * fw + (x+1)] * -1.0f;
                        
                        float result = center + top + bottom + left + right;
                        
                        // Clamp to valid range [0, 255]
                        if (result < 0.0f) result = 0.0f;
                        if (result > 255.0f) result = 255.0f;
                        
                        out_ptr[y * fw + x] = result;
                    }
                }
            }
            
            out = sharpened;
        } else {
            out = final_output;
        }
        
        return 0;
    }
};

// Global RIFE instance
static RIFENet* g_rife = nullptr;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_frameinterpolator_data_local_RIFEProcessor_nativeInitFromPath(
    JNIEnv* env,
    jobject thiz,
    jstring model_path,
    jint gpu_id
) {
    LOGI("========== RIFE INITIALIZATION FROM PATH START ==========");

    const char* path = env->GetStringUTFChars(model_path, 0);
    LOGI("Loading RIFE from path: %s", path);
    LOGI("GPU ID: %d", gpu_id);

    if (g_rife != nullptr) {
        LOGI("Deleting existing RIFE instance...");
        delete g_rife;
    }

    LOGI("Creating new RIFENet instance...");
    g_rife = new RIFENet(gpu_id, false);

    // Build full paths
    std::string param_path = std::string(path) + "/flownet.param";
    std::string bin_path = std::string(path) + "/flownet.bin";

    LOGI("Loading param from: %s", param_path.c_str());
    int ret = g_rife->flownet.load_param(param_path.c_str());
    if (ret != 0) {
        LOGE("❌ FAILED to load param from file, error code: %d", ret);
        env->ReleaseStringUTFChars(model_path, path);
        delete g_rife;
        g_rife = nullptr;
        return JNI_FALSE;
    }
    LOGI("✅ Param loaded successfully!");

    LOGI("Loading model from: %s", bin_path.c_str());
    ret = g_rife->flownet.load_model(bin_path.c_str());
    if (ret != 0) {
        LOGE("❌ FAILED to load model from file, error code: %d", ret);
        env->ReleaseStringUTFChars(model_path, path);
        delete g_rife;
        g_rife = nullptr;
        return JNI_FALSE;
    }
    LOGI("✅ Model loaded successfully!");

    env->ReleaseStringUTFChars(model_path, path);
    LOGI("✅ RIFE initialized successfully from file path with GPU: %d", gpu_id);
    LOGI("========== RIFE INITIALIZATION FROM PATH COMPLETE ==========");
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_frameinterpolator_data_local_RIFEProcessor_nativeInit(
    JNIEnv* env,
    jobject thiz,
    jobject asset_manager,
    jint gpu_id
) {
    LOGI("========== RIFE INITIALIZATION START ==========");
    LOGI("GPU ID: %d", gpu_id);

    if (g_rife != nullptr) {
        LOGI("Deleting existing RIFE instance...");
        delete g_rife;
    }

    LOGI("Creating new RIFENet instance...");
    g_rife = new RIFENet(gpu_id, false);

    LOGI("Getting AssetManager from Java...");
    AAssetManager* mgr = AAssetManager_fromJava(env, asset_manager);
    if (mgr == nullptr) {
        LOGE("CRITICAL: AssetManager is NULL!");
        delete g_rife;
        g_rife = nullptr;
        return JNI_FALSE;
    }

    LOGI("Loading RIFE model from assets/models/...");

    // Try multiple possible paths for assets
    const char* possible_paths[] = {
        "models",           // Standard path
        "assets/models",    // Full path with assets prefix
        "",                 // Root assets folder
        NULL
    };

    int ret = -1;
    for (int i = 0; possible_paths[i] != NULL; i++) {
        LOGI("Trying model path: '%s'", possible_paths[i]);
        ret = g_rife->load(mgr, possible_paths[i]);
        if (ret == 0) {
            LOGI("✅ Successfully loaded from path: '%s'", possible_paths[i]);
            break;
        }
        LOGI("Failed path '%s', trying next...", possible_paths[i]);
    }

    if (ret != 0) {
        LOGE("❌ FAILED to load RIFE model! Return code: %d", ret);
        LOGE("Make sure flownet.param and flownet.bin exist in assets/models/");
        delete g_rife;
        g_rife = nullptr;
        return JNI_FALSE;
    }

    LOGI("✅ RIFE initialized successfully with GPU: %d", gpu_id);
    LOGI("========== RIFE INITIALIZATION COMPLETE ==========");
    return JNI_TRUE;
}

JNIEXPORT jobject JNICALL
Java_com_frameinterpolator_data_local_RIFEProcessor_nativeInterpolate(
    JNIEnv* env,
    jobject thiz,
    jobject bitmap0,
    jobject bitmap1,
    jfloat timestep
) {
    if (g_rife == nullptr) {
        LOGE("RIFE not initialized");
        return nullptr;
    }

    LOGI("====== RIFE INTERPOLATION START ======");
    LOGI("Timestep: %f", timestep);

    // Get bitmap info
    AndroidBitmapInfo info0, info1;
    AndroidBitmap_getInfo(env, bitmap0, &info0);
    AndroidBitmap_getInfo(env, bitmap1, &info1);

    if (info0.width != info1.width || info0.height != info1.height) {
        LOGE("Bitmap dimensions don't match");
        return nullptr;
    }

    int width = info0.width;
    int height = info0.height;

    LOGI("Input dimensions: %dx%d", width, height);

    // Lock pixels
    void* pixels0;
    void* pixels1;
    AndroidBitmap_lockPixels(env, bitmap0, &pixels0);
    AndroidBitmap_lockPixels(env, bitmap1, &pixels1);

    LOGI("Converting bitmaps to ncnn::Mat...");

    // Convert to ncnn::Mat (RGB format)
    ncnn::Mat in0 = ncnn::Mat::from_pixels((unsigned char*)pixels0, ncnn::Mat::PIXEL_RGBA2RGB, width, height);
    ncnn::Mat in1 = ncnn::Mat::from_pixels((unsigned char*)pixels1, ncnn::Mat::PIXEL_RGBA2RGB, width, height);

    LOGI("ncnn::Mat created: in0=%dx%dx%d, in1=%dx%dx%d", in0.w, in0.h, in0.c, in1.w, in1.h, in1.c);

    AndroidBitmap_unlockPixels(env, bitmap0);
    AndroidBitmap_unlockPixels(env, bitmap1);

    // Run RIFE
    LOGI("Running RIFE neural network inference...");
    ncnn::Mat out;
    int ret = g_rife->process(in0, in1, timestep, out);
    if (ret != 0) {
        LOGE("RIFE processing failed: %d", ret);
        return nullptr;
    }
    LOGI("RIFE inference complete! Output: %dx%dx%d", out.w, out.h, out.c);

    // Create output bitmap
    jclass bitmapClass = env->FindClass("android/graphics/Bitmap");
    jmethodID createBitmap = env->GetStaticMethodID(
        bitmapClass,
        "createBitmap",
        "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;"
    );

    jclass configClass = env->FindClass("android/graphics/Bitmap$Config");
    jfieldID argb8888 = env->GetStaticFieldID(configClass, "ARGB_8888", "Landroid/graphics/Bitmap$Config;");
    jobject config = env->GetStaticObjectField(configClass, argb8888);

    jobject outputBitmap = env->CallStaticObjectMethod(
        bitmapClass,
        createBitmap,
        out.w,
        out.h,
        config
    );

    // Lock output pixels
    void* outPixels;
    AndroidBitmap_lockPixels(env, outputBitmap, &outPixels);

    LOGI("Converting ncnn::Mat to Android Bitmap...");

    // Convert ncnn::Mat to ARGB
    out.to_pixels((unsigned char*)outPixels, ncnn::Mat::PIXEL_RGB2RGBA);

    AndroidBitmap_unlockPixels(env, outputBitmap);

    LOGI("====== RIFE INTERPOLATION COMPLETE ======");
    return outputBitmap;
}

JNIEXPORT jstring JNICALL
Java_com_frameinterpolator_data_local_RIFEProcessor_nativeGetVersion(
    JNIEnv* env,
    jobject thiz
) {
    return env->NewStringUTF("RIFE Processor v1.0 (NCNN + Flownet v4.6)");
}

JNIEXPORT void JNICALL
Java_com_frameinterpolator_data_local_RIFEProcessor_nativeDestroy(
    JNIEnv* env,
    jobject thiz
) {
    if (g_rife != nullptr) {
        delete g_rife;
        g_rife = nullptr;
        LOGI("RIFE processor destroyed");
    }
}

} // extern "C"
