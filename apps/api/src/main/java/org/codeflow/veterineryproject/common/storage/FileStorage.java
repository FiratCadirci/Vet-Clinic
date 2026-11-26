package org.codeflow.veterineryproject.common.storage;

import java.io.IOException;
import java.io.InputStream;

/** Ek dosya depolama adaptörü. Anahtarlar organizasyon önekiyle oluşturulur ve çağıran tarafından doğrulanır. */
public interface FileStorage {

    String provider();

    void store(String key, InputStream content) throws IOException;

    InputStream open(String key) throws IOException;

    void delete(String key) throws IOException;
}
