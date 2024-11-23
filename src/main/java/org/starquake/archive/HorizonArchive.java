package org.starquake.archive;

import io.aeron.driver.MediaDriver;

import java.io.File;

import io.aeron.driver.ThreadingMode;
import io.aeron.archive.Archive;

public class HorizonArchive {
    public static void main(String[] args) {
        MediaDriver.Context driverContext = new MediaDriver.Context()
                .dirDeleteOnStart(true)
                .threadingMode(ThreadingMode.SHARED);

        try (MediaDriver mediaDriver = MediaDriver.launch(driverContext)) {
            System.out.println("Embedded Media Driver started");

            // Start an Aeron Archive
            Archive.Context archiveContext = new Archive.Context()
                    .aeronDirectoryName(driverContext.aeronDirectoryName())
                    .archiveDir(new File("/archive"))
                    .controlChannel("aeron:udp?endpoint=172.16.0.2:8010")
                    .localControlChannel("aeron:ipc")
                    .recordingEventsChannel("aeron:udp?endpoint=172.16.0.2:8020")
                    .replicationChannel("aeron:udp?endpoint=172.16.0.2:8030")
                    .recordingEventsEnabled(true);  // Make sure we get events

            try (Archive archive = Archive.launch(archiveContext)) {
                System.out.println("Aeron Archive started");
                Thread.currentThread().join();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
