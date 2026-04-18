// RIFE Warp layer - Full Vulkan + CPU implementation
// Based on nihui/rife-ncnn-vulkan
#include "rife_ops.h"
#include <algorithm>
#include <cmath>
#include <android/log.h>

#define LOG_TAG "WarpLayer"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#include "warp.comp.hex.h"

using namespace ncnn;

Warp::Warp()
{
    one_blob_only = false;
    support_vulkan = true;  // VULKAN ENABLED!

    pipeline_warp = 0;
    pipeline_warp_pack4 = 0;
    pipeline_warp_pack8 = 0;
}

int Warp::create_pipeline(const Option& opt)
{
    if (!vkdev)
        return 0;

    std::vector<vk_specialization_type> specializations(0 + 0);

    // pack1
    {
        static std::vector<uint32_t> spirv;
        static ncnn::Mutex lock;
        {
            ncnn::MutexLockGuard guard(lock);
            if (spirv.empty())
            {
                compile_spirv_module((const char*)warp_comp_data, sizeof(warp_comp_data), opt, spirv);
            }
        }

        pipeline_warp = new Pipeline(vkdev);
        pipeline_warp->set_optimal_local_size_xyz();
        pipeline_warp->create(spirv.data(), spirv.size() * 4, specializations);
    }

    // pack4
    {
        pipeline_warp_pack4 = new Pipeline(vkdev);
        pipeline_warp_pack4->set_optimal_local_size_xyz();
        // Reuse pack1 shader for pack4
        static std::vector<uint32_t> spirv;
        static ncnn::Mutex lock;
        {
            ncnn::MutexLockGuard guard(lock);
            if (spirv.empty())
            {
                compile_spirv_module((const char*)warp_comp_data, sizeof(warp_comp_data), opt, spirv);
            }
        }
        pipeline_warp_pack4->create(spirv.data(), spirv.size() * 4, specializations);
    }

    // pack8 (optional - skip if not supported)
    pipeline_warp_pack8 = 0;

    return 0;
}

int Warp::destroy_pipeline(const Option& opt)
{
    delete pipeline_warp;
    pipeline_warp = 0;

    delete pipeline_warp_pack4;
    pipeline_warp_pack4 = 0;

    delete pipeline_warp_pack8;
    pipeline_warp_pack8 = 0;

    return 0;
}

// CPU implementation
int Warp::forward(const std::vector<Mat>& bottom_blobs, std::vector<Mat>& top_blobs, const Option& opt) const
{
    const Mat& image_blob = bottom_blobs[0];
    const Mat& flow_blob = bottom_blobs[1];

    int w = image_blob.w;
    int h = image_blob.h;
    int channels = image_blob.c;

    LOGI("⚠️ WARP CPU FORWARD CALLED (NOT GPU!) w=%d, h=%d, c=%d", w, h, channels);

    Mat& top_blob = top_blobs[0];
    top_blob.create(w, h, channels, 4u, opt.blob_allocator);
    if (top_blob.empty())
        return -100;

    #pragma omp parallel for num_threads(opt.num_threads)
    for (int q = 0; q < channels; q++)
    {
        float* outptr = top_blob.channel(q);

        const Mat image = image_blob.channel(q);

        const float* fxptr = flow_blob.channel(0);
        const float* fyptr = flow_blob.channel(1);

        for (int y = 0; y < h; y++)
        {
            for (int x = 0; x < w; x++)
            {
                float flow_x = fxptr[y * w + x];
                float flow_y = fyptr[y * w + x];

                float sample_x = x + flow_x;
                float sample_y = y + flow_y;

                // bilinear interpolation
                float v;
                {
                    int x0 = floor(sample_x);
                    int y0 = floor(sample_y);
                    int x1 = x0 + 1;
                    int y1 = y0 + 1;

                    x0 = std::min(std::max(x0, 0), w - 1);
                    y0 = std::min(std::max(y0, 0), h - 1);
                    x1 = std::min(std::max(x1, 0), w - 1);
                    y1 = std::min(std::max(y1, 0), h - 1);

                    float alpha = sample_x - x0;
                    float beta = sample_y - y0;

                    float v0 = image.row(y0)[x0];
                    float v1 = image.row(y0)[x1];
                    float v2 = image.row(y1)[x0];
                    float v3 = image.row(y1)[x1];

                    float v4 = v0 * (1 - alpha) + v1 * alpha;
                    float v5 = v2 * (1 - alpha) + v3 * alpha;

                    v = v4 * (1 - beta) + v5 * beta;
                }

                outptr[y * w + x] = v;
            }
        }
    }

    return 0;
}

// Vulkan GPU implementation
int Warp::forward(const std::vector<VkMat>& bottom_blobs, std::vector<VkMat>& top_blobs, VkCompute& cmd, const Option& opt) const
{
    const VkMat& image_blob = bottom_blobs[0];
    const VkMat& flow_blob = bottom_blobs[1];

    int w = image_blob.w;
    int h = image_blob.h;
    int channels = image_blob.c;
    size_t elemsize = image_blob.elemsize;
    int elempack = image_blob.elempack;

    LOGI("🔥 WARP VULKAN FORWARD CALLED! w=%d, h=%d, c=%d, elempack=%d", w, h, channels, elempack);

    VkMat& top_blob = top_blobs[0];
    top_blob.create(w, h, channels, elemsize, elempack, opt.blob_vkallocator);
    if (top_blob.empty())
        return -100;

    std::vector<VkMat> bindings(3);
    bindings[0] = image_blob;
    bindings[1] = flow_blob;
    bindings[2] = top_blob;

    std::vector<vk_constant_type> constants(4);
    constants[0].i = top_blob.w;
    constants[1].i = top_blob.h;
    constants[2].i = top_blob.c;
    constants[3].i = top_blob.cstep;

    if (elempack == 8)
    {
        cmd.record_pipeline(pipeline_warp_pack8, bindings, constants, top_blob);
    }
    else if (elempack == 4)
    {
        cmd.record_pipeline(pipeline_warp_pack4, bindings, constants, top_blob);
    }
    else // if (elempack == 1)
    {
        cmd.record_pipeline(pipeline_warp, bindings, constants, top_blob);
    }

    return 0;
}
