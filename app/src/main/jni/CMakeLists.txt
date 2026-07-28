cmake_minimum_required(VERSION 3.22.1)
project(pipe_yolo)

set(NCNN_DIR ${CMAKE_SOURCE_DIR}/ncnn)
add_library(ncnn STATIC IMPORTED)
set_target_properties(ncnn PROPERTIES
    IMPORTED_LOCATION ${NCNN_DIR}/${ANDROID_ABI}/libncnn.a
)

add_library(pipe_yolo SHARED yolo_ncnn.cpp)

find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)
find_library(android-lib android)

target_include_directories(pipe_yolo PRIVATE ${NCNN_DIR}/include)
# ncnn prebuilt needs OpenMP; NDK provides -static-openmp
target_compile_options(pipe_yolo PRIVATE -fno-rtti -fno-exceptions -fopenmp)
target_link_options(pipe_yolo PRIVATE -fopenmp -static-openmp)
target_link_libraries(pipe_yolo ncnn ${log-lib} ${jnigraphics-lib} ${android-lib})

set_target_properties(pipe_yolo PROPERTIES CXX_STANDARD 11)
