package im.metersphere.plugin.storage;
import org.apache.commons.io.FileUtils;

import java.io.*;

/**
 * jar包静态资源存储策略，存储在本地磁盘中
 */
public class LocalStorageStrategy implements StorageStrategy {

    private String dirPath;

    public LocalStorageStrategy(String dirPath) {
        this.dirPath = dirPath + (dirPath.endsWith(File.separator) ? "" : File.separator);
    }

    @Override
    public String store(String name, InputStream in) throws IOException {
        String path = dirPath + name;
        FileUtils.writeByteArrayToFile(new File(path),  in.readAllBytes());
        return path;
    }

    @Override
    public InputStream get(String path) throws FileNotFoundException {
        return new FileInputStream(dirPath + path);
    }

    @Override
    public void delete() throws IOException {
        FileUtils.deleteDirectory(new File(dirPath));
    }
}
