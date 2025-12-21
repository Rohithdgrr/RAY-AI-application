package com.example.offlinellm;

import android.content.Context;
import androidx.security.crypto.EncryptedFile;
import androidx.security.crypto.MasterKey;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class SecurityHelper {
    public static void encryptFile(Context context, File sourceFile, File encryptedFile) throws Exception {
        MasterKey masterKey = new MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build();

        EncryptedFile encFile = new EncryptedFile.Builder(
                context,
                encryptedFile,
                masterKey,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build();

        try (InputStream in = new FileInputStream(sourceFile);
             OutputStream out = encFile.openFileOutput()) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
        }
    }

    public static void decryptFile(Context context, File encryptedFile, File targetFile) throws Exception {
        MasterKey masterKey = new MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build();

        EncryptedFile encFile = new EncryptedFile.Builder(
                context,
                encryptedFile,
                masterKey,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build();

        try (InputStream in = encFile.openFileInput();
             OutputStream out = new FileOutputStream(targetFile)) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
        }
    }
}
