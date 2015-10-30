package com.griddynamics.jagger.util;

import java.io.Closeable;
import java.io.IOException;

public class IOUtils {

    public static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                // do nothing
            }
        }
    }

}
