cmake_minimum_required(VERSION 3.22.1)

project("rife_processor")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Add compiler flags
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fno-rtti -fno-exceptions -Ofast -fopenmp")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Ofast -fopenmp")

# NCNN paths
set(NCNN_DIR ${CMAKE_CURRENT_SOURCE_DIR}/ncnn)
set(NCNN_INCLUDE_DIR ${NCNN_DIR}/include)
set(NCNN_LIB_DIR ${NCNN_DIR}/lib/${ANDROID_ABI})

# Include NCNN headers
include_directories(${NCNN_INCLUDE_DIR})

# Find required libraries
find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)
find_library(android-lib android)
find_library(vulkan-lib vulkan)

# Add NCNN library
add_library(ncnn STATIC IMPORTED)
set_target_properties(ncnn PROPERTIES IMPORTED_LOCATION ${NCNN_LIB_DIR}/libncnn.a)

# Add glslang libraries
add_library(glslang STATIC IMPORTED)
set_target_properties(glslang PROPERTIES IMPORTED_LOCATION ${NCNN_LIB_DIR}/libglslang.a)

add_library(MachineIndependent STATIC IMPORTED)
set_target_properties(MachineIndependent PROPERTIES IMPORTED_LOCATION ${NCNN_LIB_DIR}/libMachineIndependent.a)

add_library(GenericCodeGen STATIC IMPORTED)
set_target_properties(GenericCodeGen PROPERTIES IMPORTED_LOCATION ${NCNN_LIB_DIR}/libGenericCodeGen.a)

add_library(OSDependent STATIC IMPORTED)
set_target_properties(OSDependent PROPERTIES IMPORTED_LOCATION ${NCNN_LIB_DIR}/libOSDependent.a)

add_library(SPIRV STATIC IMPORTED)
set_target_properties(SPIRV PROPERTIES IMPORTED_LOCATION ${NCNN_LIB_DIR}/libSPIRV.a)

# Add our native source
add_library(
    rife_processor
    SHARED
    rife_processor.cpp
    warp.cpp
)

# Link all libraries (order matters!)
target_link_libraries(
    rife_processor
    ncnn
    glslang
    SPIRV
    MachineIndependent
    OSDependent
    GenericCodeGen
    ${vulkan-lib}
    ${log-lib}
    ${jnigraphics-lib}
    ${android-lib}
    -fopenmp
    -static-openmp
)
