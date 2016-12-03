#include "neon-intrinsics.h"
#include <arm_neon.h>
#include <malloc.h>
#include <string.h>
#include <android/bitmap.h>
#include <android/log.h>

#define  LOG_TAG    "DEBUG"
#define  LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG,LOG_TAG,__VA_ARGS__)
/* this source file should only be compiled by Android.mk /CMake when targeting
 * the armeabi-v7a ABI, and should be built in NEON mode
 */

void fir_filter_neon_intrinsics(short *output, const short* input, const short* kernel, int width, int kernelSize)
{
#if 1
    int nn, offset = -kernelSize/2;

    for (nn = 0; nn < width; nn++)
    {
        int mm, sum = 0;
        int32x4_t sum_vec = vdupq_n_s32(0);
        for(mm = 0; mm < kernelSize/4; mm++)
        {
            int16x4_t  kernel_vec = vld1_s16(kernel + mm*4);
            int16x4_t  input_vec = vld1_s16(input + (nn+offset+mm*4));
            sum_vec = vmlal_s16(sum_vec, kernel_vec, input_vec);
        }

        sum += vgetq_lane_s32(sum_vec, 0);
        sum += vgetq_lane_s32(sum_vec, 1);
        sum += vgetq_lane_s32(sum_vec, 2);
        sum += vgetq_lane_s32(sum_vec, 3);

        if(kernelSize & 3)
        {
            for(mm = kernelSize - (kernelSize & 3); mm < kernelSize; mm++)
                sum += kernel[mm] * input[nn+offset+mm];
        }

        output[nn] = (short)((sum + 0x8000) >> 16);
    }
#else /* for comparison purposes only */
    int nn, offset = -kernelSize/2;
    for (nn = 0; nn < width; nn++) {
        int sum = 0;
        int mm;
        for (mm = 0; mm < kernelSize; mm++) {
            sum += kernel[mm]*input[nn+offset+mm];
        }
        output[n] = (short)((sum + 0x8000) >> 16);
    }
#endif
}



void neonNeonEdgeLinearSum(AndroidBitmapInfo* infoOri, uint32_t * pixelsOri,AndroidBitmapInfo* infoProcessed, uint32_t * pixelsProcessed,float f1,float f2){
    uint32_t  width = infoOri->width;
    uint32_t  height = infoOri->height;
    uint32x4_t redFactor = vdupq_n_u32(0x00FF0000);
    uint32x4_t greenFactor = vdupq_n_u32(0x0000FF00);
    uint32x4_t blueFactor = vdupq_n_u32(0x000000FF);
    float32x4_t f1V =  vmovq_n_f32((float32_t)f1);
    float32x4_t f2V =  vmovq_n_f32((float32_t)f2);
    uint32_t* linePro;
    uint32_t* lineOri;


    for(int i = 0; i<height; i++) {
        linePro = (uint32_t*) pixelsProcessed;
        lineOri = (uint32_t*) pixelsOri;
        for(int j = 0; j<width/4;j += 4) {

            uint32x4_t ori = vld1q_u32(&lineOri[j]);
            uint32x4_t redOri = vandq_u32(ori, redFactor);
            uint32x4_t greenOri = vandq_u32(ori, greenFactor);
            uint32x4_t blueOri = vandq_u32(ori, blueFactor);

            uint32x4_t pre = vld1q_u32(&linePro[j]);
            uint32x4_t redPre = vandq_u32(pre, redFactor);
            uint32x4_t greenPre = vandq_u32(pre, greenFactor);
            uint32x4_t bluePre = vandq_u32(pre, blueFactor);

            float32x4_t red = vmlaq_f32(vmulq_f32(redPre , f1V), redOri, f2V);
            float32x4_t green = vmlaq_f32(vmulq_f32(greenPre , f1V), greenOri, f2V);
            float32x4_t blue = vmlaq_f32(vmulq_f32(bluePre , f1V), blueOri, f2V);


            //uint8x8_t a =  vdup_n_u8(0);
        }
        pixelsProcessed = (uint32_t*)((char*)pixelsProcessed + infoProcessed->stride);
        pixelsOri = (uint32_t*)((char*)pixelsOri + infoOri->stride);
    }

}

