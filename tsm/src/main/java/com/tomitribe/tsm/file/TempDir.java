/*
 * Tomitribe Confidential
 *
 * Copyright Tomitribe Corporation. 2015
 *
 * The source code for this program is not published or otherwise divested
 * of its trade secrets, irrespective of what has been deposited with the
 * U.S. Copyright Office.
 */
package com.tomitribe.tsm.file;

import org.tomitribe.util.Files;

import java.io.File;

public interface TempDir {
    static File newTempDir(final File workDirBase, final String nameBase) {
        File workDir;
        do {
            workDir = new File(workDirBase, nameBase + '-' + Math.abs(System.nanoTime()));
        } while (workDir.exists());
        Files.mkdirs(workDir);

        final File dir = workDir;
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                if (dir.isDirectory()) {
                    Files.remove(dir);
                }
            }
        });
        return workDir;
    }
}
