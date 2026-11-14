package pro.sketchware.filemanagers;

import android.content.Context;
import android.util.Log;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class FileCryptoUtils {

    private static final String CRYPTO_ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final String CRYPTO_KEY = "sketchwaresecure";
    private static final int BUFFER_SIZE = 1024;

    public boolean verbose = false;

    public FileCryptoUtils() {
        this(false);
    }

    public FileCryptoUtils(boolean verbose) {
        this.verbose = verbose;
    }

    public long a(Context context, String assetPath) {
        InputStream stream = null;
        try {
            stream = context.getAssets().open(assetPath);
            return stream.available();
        } catch (IOException ex) {
            ex.printStackTrace();
            return -1L;
        } finally {
            closeQuietly(stream);
        }
    }

    public String a(byte[] data) {
        byte[] decoded = this.b(data);
        if (decoded == null) {
            return "";
        }
        try {
            return new String(decoded, "UTF-8");
        } catch (UnsupportedEncodingException ex) {
            ex.printStackTrace();
            return "";
        }
    }

    public void a(Context context, String assetPath, String destPath) {
        ensureParentDir(destPath);
        InputStream in = null;
        FileOutputStream out = null;
        try {
            in = context.getAssets().open(assetPath);
            out = new FileOutputStream(destPath);
            byte[] buffer = new byte[BUFFER_SIZE];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
            if (this.verbose) {
                log("assetFile =>" + destPath + " copy success.");
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            closeQuietly(in);
            closeQuietly(out);
        }
    }

    public void a(File directory) {
        this.a(directory, true);
    }

    public void a(File source, File destination) {
        try {
            if (source.isDirectory()) {
                if (!destination.exists() && !destination.mkdirs()) {
                    throw new IOException("Cannot create dir " + destination.getAbsolutePath());
                }
                String[] children = source.list();
                if (children != null) {
                    for (String child : children) {
                        this.a(new File(source, child), new File(destination, child));
                    }
                }
            } else {
                this.a(source.getAbsolutePath(), destination.getAbsolutePath());
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public void a(File target, boolean includeSelf) {
        if (!target.exists()) {
            return;
        }
        File[] children = target.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) {
                    this.a(child);
                } else if (child.isFile()) {
                    boolean deleted = child.delete();
                    if (this.verbose) {
                        log((deleted ? "Delete file success." : "Delete file failed.") + child.getAbsolutePath());
                    }
                }
            }
        }
        if (includeSelf) {
            target.delete();
        }
    }

    public void a(String path) {
        this.b(path);
        this.f(path);
    }

    public void a(String sourcePath, String destPath) {
        FileInputStream in = null;
        FileOutputStream out = null;
        try {
            in = new FileInputStream(sourcePath);
            out = new FileOutputStream(destPath);
            byte[] buffer = new byte[BUFFER_SIZE];
            int length;
            if (this.verbose) {
                log("src=" + sourcePath + ",dest=" + destPath);
            }
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            closeQuietly(in);
            closeQuietly(out);
        }
    }

    public void a(String path, boolean includeSelf) {
        this.a(new File(path), includeSelf);
    }

    public void a(String path, byte[] data) {
        if (data == null) {
            return;
        }
        ensureParentDir(path);
        File file = new File(path);
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException ignored) {
            }
        }
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
            out.write(data);
            out.flush();
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            closeQuietly(out);
        }
    }

    public String b(Context context, String assetPath) {
        StringBuilder builder = new StringBuilder();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(context.getAssets().open(assetPath.trim()), "utf-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
                builder.append("\r\n");
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            closeQuietly(reader);
        }
        return builder.toString();
    }

    public void b(File file) {
        file.delete();
    }

    public void b(String path) {
        this.a(path, true);
    }

    public void b(String path, String content) {
        ensureParentDir(path);
        File file = new File(path);
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException ignored) {
            }
        }
        FileWriter writer = null;
        try {
            writer = new FileWriter(file);
            writer.write(content);
            writer.flush();
            if (this.verbose) {
                log(path + " saved");
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            closeQuietly(writer);
        }
    }

    public byte[] b(byte[] data) {
        return transform(Cipher.DECRYPT_MODE, data);
    }

    public String c(File file) {
        StringBuilder builder = new StringBuilder();
        FileReader reader = null;
        try {
            reader = new FileReader(file);
            char[] buffer = new char[BUFFER_SIZE];
            int length;
            while ((length = reader.read(buffer)) > 0) {
                builder.append(buffer, 0, length);
            }
        } catch (IOException ignored) {
        } finally {
            closeQuietly(reader);
        }
        return builder.toString();
    }

    public void c(String path) {
        this.b(new File(path));
    }

    public byte[] c(byte[] data) {
        return transform(Cipher.ENCRYPT_MODE, data);
    }

    public byte[] d(String text) {
        try {
            return this.c(text.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException ex) {
            ex.printStackTrace();
            return null;
        }
    }

    public boolean e(String path) {
        return new File(path).exists();
    }

    public boolean f(String path) {
        return !this.e(path) ? new File(path).mkdirs() : false;
    }

    public String g(String path) {
        return this.c(new File(path));
    }

    public byte[] h(String path) {
        FileInputStream in = null;
        try {
            in = new FileInputStream(new File(path));
            int size = in.available();
            if (size <= 0) {
                return null;
            }
            byte[] data = new byte[size];
            in.read(data);
            return data;
        } catch (Exception ex) {
            return null;
        } finally {
            closeQuietly(in);
        }
    }

    private byte[] transform(int mode, byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance(CRYPTO_ALGORITHM);
            byte[] key = CRYPTO_KEY.getBytes();
            cipher.init(mode, new SecretKeySpec(key, "AES"), new IvParameterSpec(key));
            return cipher.doFinal(data);
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    private void ensureParentDir(String path) {
        int slashIndex = path.lastIndexOf(File.separator);
        if (slashIndex > 0) {
            this.f(path.substring(0, slashIndex));
        }
    }

    private void log(String message) {
        Log.d(getClass().getSimpleName(), message);
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignored) {
            }
        }
    }
}
