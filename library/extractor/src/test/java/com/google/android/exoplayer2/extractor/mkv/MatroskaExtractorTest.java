/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.extractor.mkv;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.testutil.ExtractorAsserts;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link MatroskaExtractor}. */
@RunWith(AndroidJUnit4.class)
public final class MatroskaExtractorTest {

  @Test
  public void mkvSample() throws Exception {
    ExtractorAsserts.assertBehavior(MatroskaExtractor::new, "mkv/sample.mkv");
  }

  @Test
  public void mkvSample_withSubripSubtitles() throws Exception {
    ExtractorAsserts.assertBehavior(MatroskaExtractor::new, "mkv/sample_with_srt.mkv");
  }

  @Test
  public void mkvSample_withHtcRotationInfoInTrackName() throws Exception {
    ExtractorAsserts.assertBehavior(
        MatroskaExtractor::new, "mkv/sample_with_htc_rotation_track_name.mkv");
  }

  @Test
  public void mkvFullBlocksSample() throws Exception {
    ExtractorAsserts.assertBehavior(MatroskaExtractor::new, "mkv/full_blocks.mkv");
  }

  @Test
  public void webmSubsampleEncryption() throws Exception {
    ExtractorAsserts.assertBehavior(
        MatroskaExtractor::new, "mkv/subsample_encrypted_noaltref.webm");
  }

  @Test
  public void webmSubsampleEncryptionWithAltrefFrames() throws Exception {
    ExtractorAsserts.assertBehavior(MatroskaExtractor::new, "mkv/subsample_encrypted_altref.webm");
  }
}
