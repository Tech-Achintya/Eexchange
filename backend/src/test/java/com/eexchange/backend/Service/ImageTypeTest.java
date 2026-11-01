package com.eexchange.backend.Service;

import com.eexchange.backend.Service.Storage.ImageType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImageTypeTest {

    @Test
    void detectsJpeg() {
        byte[] bytes = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};

        assertThat(ImageType.detect(bytes)).contains(ImageType.JPEG);
    }

    @Test
    void detectsPng() {
        byte[] bytes = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

        assertThat(ImageType.detect(bytes)).contains(ImageType.PNG);
    }

    @Test
    void detectsWebp() {
        byte[] bytes = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};

        assertThat(ImageType.detect(bytes)).contains(ImageType.WEBP);
    }

    @Test
    void rejectsRenamedExe() {
        // A .exe renamed to .jpg still starts with "MZ".
        byte[] bytes = {'M', 'Z', (byte) 0x90, 0x00, 0x03};

        assertThat(ImageType.detect(bytes)).isEmpty();
    }

    @Test
    void rejectsTooShortAndNull() {
        assertThat(ImageType.detect(new byte[]{(byte) 0xFF, (byte) 0xD8})).isEmpty();
        assertThat(ImageType.detect(null)).isEmpty();
    }
}