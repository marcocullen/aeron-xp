package org.starquake.archive;

import io.aeron.driver.MediaDriver;

import java.io.File;

import io.aeron.driver.ThreadingMode;
import io.aeron.archive.Archive;

public class HorizonArchive {
    public static void main(String[] args) {
        try {
            // Start an embedded Media Driver
            MediaDriver.Context driverContext = new MediaDriver.Context()
                    .dirDeleteOnStart(true)
                    .threadingMode(ThreadingMode.SHARED);

            MediaDriver mediaDriver = MediaDriver.launch(driverContext);
            System.out.println("Embedded Media Driver started");

            // Start an Aeron Archive
            Archive.Context archiveContext = new Archive.Context()
                    .archiveDir(new File(System.getProperty("user.dir"), "archive"))
                    .controlChannel("aeron:udp?endpoint=182.16.0.10:8010")
                    .localControlChannel("aeron:ipc")
                    .recordingEventsChannel("aeron:udp?endpoint=localhost:8020");

            Archive archive = Archive.launch(archiveContext);
            System.out.println("Aeron Archive started");

            // Keep the application running
            Thread.currentThread().join();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
