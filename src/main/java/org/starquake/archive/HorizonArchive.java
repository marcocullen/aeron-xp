package org.starquake.archive;

import io.aeron.driver.MediaDriver;

import java.io.File;

public class HorizonArchive {
    public static void main(String[] args) {
        try {
            // Start an embedded Media Driver
            MediaDriver.Context driverContext = new io.aeron.driver.MediaDriver.Context()
                    .dirDeleteOnStart(true)
                    .threadingMode(io.aeron.driver.ThreadingMode.SHARED);

            MediaDriver mediaDriver = io.aeron.driver.MediaDriver.launch(driverContext);
            System.out.println("Embedded Media Driver started");

            // Start an Aeron Archive
            io.aeron.archive.Archive.Context archiveContext = new io.aeron.archive.Archive.Context()
                    .archiveDir(new File(System.getProperty("user.dir"), "archive"))
                    .controlChannel("aeron:udp?endpoint=182.16.0.10:8010")
                    .localControlChannel("aeron:ipc")
                    .recordingEventsChannel("aeron:udp?endpoint=localhost:8020");

            io.aeron.archive.Archive archive = io.aeron.archive.Archive.launch(archiveContext);
            System.out.println("Aeron Archive started");

            // Keep the application running
            Thread.currentThread().join();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
