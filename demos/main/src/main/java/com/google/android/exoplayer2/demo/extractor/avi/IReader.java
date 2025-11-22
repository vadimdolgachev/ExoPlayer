package com.google.android.exoplayer2.demo.extractor.avi;

import androidx.annotation.NonNull;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import java.io.IOException;

public interface IReader {
    long getPosition();

    /**
     * @return true if the reader is complete
     */
    boolean read(@NonNull ExtractorInput input) throws IOException;
}