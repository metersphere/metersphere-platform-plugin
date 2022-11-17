package im.metersphere.plugin.storage;

import java.io.IOException;
import java.io.InputStream;

public interface StorageStrategy {

    String store(String name, InputStream in) throws IOException;

    InputStream get(String path) throws IOException;

    void delete() throws IOException;
}
