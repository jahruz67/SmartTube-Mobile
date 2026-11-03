package arte.programar.materialfile.utils;

import java.io.File;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;

public class FileComparator implements Comparator<File> {
    private final Map<File, FileInfo> mSnapshot;

    public FileComparator() {
        mSnapshot = null;
    }

    public FileComparator(File[] files) {
        mSnapshot = new IdentityHashMap<>();
        for (File file : files) {
            checkCancelled();
            mSnapshot.put(file, new FileInfo(file));
        }
    }

    @Override
    public int compare(File f1, File f2) {
        if (f1 == f2) {
            return 0;
        }
        checkCancelled();
        FileInfo first = info(f1);
        FileInfo second = info(f2);
        if (first.directory && second.file) {
            // Show directories above files
            return -1;
        }
        if (first.file && second.directory) {
            // Show files below directories
            return 1;
        }
        // Sort the directories alphabetically
        return first.name.compareToIgnoreCase(second.name);
    }

    private FileInfo info(File file) {
        FileInfo info = mSnapshot != null ? mSnapshot.get(file) : null;
        return info != null ? info : new FileInfo(file);
    }

    private void checkCancelled() {
        if (mSnapshot != null && Thread.currentThread().isInterrupted()) {
            throw new CancellationException();
        }
    }

    private static final class FileInfo {
        final boolean directory;
        final boolean file;
        final String name;

        FileInfo(File value) {
            directory = value.isDirectory();
            file = value.isFile();
            name = value.getName();
        }
    }
}
