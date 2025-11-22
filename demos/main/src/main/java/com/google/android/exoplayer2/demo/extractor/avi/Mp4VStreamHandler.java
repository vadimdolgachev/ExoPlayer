/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.demo.extractor.avi;

import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.util.ParsableNalUnitBitArray;
import java.io.IOException;

/**
 * Peeks an MP4V stream looking for pixelWidthHeightRatio data
 */
public class Mp4VStreamHandler extends NalStreamHandler {
    @VisibleForTesting
    static final byte SEQUENCE_START_CODE = (byte) 0xb0;
    @VisibleForTesting
    static final byte VOP_START_CODE = (byte) 0xb6;
    @VisibleForTesting
    static final int LAYER_START_CODE = 0x20;
    private static final float[] ASPECT_RATIO = {0f, 1f, 12f / 11f, 10f / 11f, 16f / 11f, 40f / 33f};

    private static final byte SIMPLE_PROFILE_MASK = 0b1111;
    private static final int SHAPE_TYPE_GRAYSCALE = 3;
    static final int VOP_TYPE_B = 2;

    @VisibleForTesting
    static final int Extended_PAR = 0xf;

    private final Format.Builder formatBuilder;

    // This chipset(device?) seems to correct the clock for B-Frame inside the codec.
    // Not sure if it's the whole series (MediaTek P35) or just this chipset.
    private static boolean enableStreamClock = !Build.HARDWARE.equals("mt6765")
//            && !Build.HARDWARE.equals("amlogic")
            ; //Samsung A7 Lite

    @VisibleForTesting()
    float pixelWidthHeightRatio = 1f;

    int vopTimeIncrementBits;
    int vopTimeIncrementResolution;

    long clockOffsetUs;
    long frameOffsetUs;
    // modulo_time_base from the last I/P frame, used to calculate the time offset of B-frames
    int priorModulo;

    public Mp4VStreamHandler(int id, long durationUs, @NonNull TrackOutput trackOutput,
                             @NonNull Format.Builder formatBuilder) {
        super(id, durationUs, trackOutput, 12);
        this.formatBuilder = formatBuilder;
    }

    @Override
    boolean skip(byte nalType) {
        return nalType != SEQUENCE_START_CODE && (!useStreamClock || nalType != VOP_START_CODE);
    }

    @Override
    void reset() {
        clockOffsetUs = frameOffsetUs = 0L;
    }

    @Override
    public long getTimeUs() {
//        if (useStreamClock) {
//            return super.getTimeUs() + frameOffsetUs;
//        } else {
//            return super.getTimeUs();
//        }
        return super.getTimeUs() + frameOffsetUs;
    }

    @Override
    protected void advanceTime() {
        if (!useStreamClock) {
            super.advanceTime();
        }
    }

    void readMarkerBit(@NonNull final ParsableNalUnitBitArray in) {
        if (!in.readBit()) {
            throw new IllegalStateException("Marker Bit false");
        }
    }

    @VisibleForTesting
    void parseVideoObjectLayer(int nalTypeOffset) {
        @NonNull final ParsableNalUnitBitArray in = new ParsableNalUnitBitArray(buffer, nalTypeOffset + 1, pos);
        in.skipBit(); // random_accessible_vol
        in.skipBits(8); // video_object_type_indication
        boolean isObjectLayerIdentifier = in.readBit();
        int videoObjectLayerVerid = 0;
        if (isObjectLayerIdentifier) {
            videoObjectLayerVerid = in.readBits(4);
            in.skipBits(3); // video_object_layer_priority
        }
        int aspectRatioInfo = in.readBits(4);
        final float aspectRatio;
        if (aspectRatioInfo == Extended_PAR) {
            float par_width = (float) in.readBits(8);
            float par_height = (float) in.readBits(8);
            aspectRatio = par_width / par_height;
        } else {
            aspectRatio = ASPECT_RATIO[aspectRatioInfo];
        }
        if (aspectRatio != pixelWidthHeightRatio) {
            trackOutput.format(formatBuilder.setPixelWidthHeightRatio(aspectRatio).build());
            pixelWidthHeightRatio = aspectRatio;
        }
        //vol_control_parameters
        if (in.readBit()) {
            in.skipBits(2 + 1); //chroma_format, low_delay
            //vbv_parameters
            if (in.readBit()) {
                in.skipBits(15 + 1 + 15 + 1 + 1 + 3 + 11 + 1 + 15 + 1); //first_half_bit_rate...
            }
        }
        int videoObjectLayerShape = in.readBits(2);
        if (videoObjectLayerShape == SHAPE_TYPE_GRAYSCALE && videoObjectLayerVerid != 1) {
            in.skipBits(4); //video_object_layer_shape_extension
        }
        readMarkerBit(in);
        vopTimeIncrementResolution = in.readBits(16);
        vopTimeIncrementBits = (int) ((Math.log(vopTimeIncrementResolution) / Math.log(2))) + 1;
    }

    void parseVideoObjectPlane(int nalTypeOffset) {
        final ParsableNalUnitBitArray in = new ParsableNalUnitBitArray(buffer, nalTypeOffset + 1, buffer.length);
        final int vopCodingType = in.readBits(2);
        // Usually this is 0, but on clock advance is 1
        int moduloTimeBase = 0;
        while (in.readBit()) {
            moduloTimeBase++;
        }
        readMarkerBit(in);
        int vopTimeIncrement = in.readBits(vopTimeIncrementBits);
        long frameUs = C.MICROS_PER_SECOND * vopTimeIncrement / vopTimeIncrementResolution;
        if (vopCodingType == VOP_TYPE_B) {
            if (priorModulo != moduloTimeBase) {
                // Subtract the modulo delta from the clock offset.
                frameUs -= (priorModulo - moduloTimeBase) * C.MICROS_PER_SECOND;
            }
        } else {
            priorModulo = moduloTimeBase;
            if (moduloTimeBase != 0) {
                clockOffsetUs += moduloTimeBase * C.MICROS_PER_SECOND;
            }
        }
        frameOffsetUs = clockOffsetUs + frameUs;
    }

    @Override
    void processChunk(ExtractorInput input, int nalTypeOffset) throws IOException {
        while (true) {
            final byte nalType = buffer[nalTypeOffset];
            if (useStreamClock && nalType == VOP_START_CODE) {
                parseVideoObjectPlane(nalTypeOffset);
                break;
            } else if (nalType == SEQUENCE_START_CODE) {
                final byte profileAndLevelIndication = buffer[nalTypeOffset + 1];
                useStreamClock = enableStreamClock && (profileAndLevelIndication & SIMPLE_PROFILE_MASK) != profileAndLevelIndication;
            } else if ((nalType & 0xf0) == LAYER_START_CODE) {
                //Read the whole NAL into the buffer
                seekNextNal(input, nalTypeOffset);
                parseVideoObjectLayer(nalTypeOffset);
                // There may be a VOP start code after this NAL, so if we are tracking B frames, don't exit
                if (useStreamClock) {
                    // Due to seekNextNal() above the pointer should be at the next NAL offset
                    nalTypeOffset = 0;
                } else {
                    break;
                }
            }

            nalTypeOffset = seekNextNal(input, nalTypeOffset);
            if (nalTypeOffset < 0) {
                break;
            }
            compact();
        }
    }
}
