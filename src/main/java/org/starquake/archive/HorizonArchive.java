package org.starquake.archive;

import io.aeron.Aeron;
import io.aeron.archive.client.AeronArchive;
import io.aeron.driver.MediaDriver;

import java.io.File;

import io.aeron.driver.ThreadingMode;
import io.aeron.archive.Archive;

public class HorizonArchive {

    public static void main(String[] args) {
        MediaDriver.Context driverContext = new MediaDriver.Context()
                .publicationTermBufferLength(64 * 1024) // Set term length to 64 KiB
                .dirDeleteOnStart(true)
                .threadingMode(ThreadingMode.SHARED);

        try (MediaDriver mediaDriver = MediaDriver.launch(driverContext)) {
            System.out.println("Embedded Media Driver started");

            Archive.Context archiveContext = new Archive.Context()
                    .aeronDirectoryName(driverContext.aeronDirectoryName())
                    .archiveDir(new File("/archive"))
                    .segmentFileLength(64 * 1024)
                    .controlChannel("aeron:udp?endpoint=0.0.0.0:8010")
                    .localControlChannel("aeron:ipc")
                    .recordingEventsChannel("aeron:udp?endpoint=localhost:8020")
                    .replicationChannel("aeron:udp?endpoint=localhost:8030")
                    .recordingEventsEnabled(true);  // They should be enabled; default is false

            try (Archive archive = Archive.launch(archiveContext);
                 Aeron aeron = Aeron.connect(new Aeron.Context().aeronDirectoryName(driverContext.aeronDirectoryName()));
                 AeronArchive aeronArchive = AeronArchive.connect(
                         new AeronArchive.Context()
                                 .aeron(aeron)
                                 .controlRequestChannel(archiveContext.controlChannel())
                                 .controlResponseChannel("aeron:udp?endpoint=localhost:0"))) {

                System.out.println("Aeron Archive started");

                // Create ArchiveMonitor with its own control response channel
                try (final ArchiveMonitor monitor = new ArchiveMonitor(
                        driverContext.aeronDirectoryName(),
                        archiveContext.controlChannel(),
                        "aeron:udp?endpoint=localhost:0")) {

                    // Main loop
                    while (true) {
                        monitor.doWork();
                        Thread.sleep(100); // Avoid tight loop
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
