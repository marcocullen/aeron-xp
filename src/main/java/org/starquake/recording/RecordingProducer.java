package org.starquake.recording;

import io.aeron.Publication;
import io.aeron.archive.codecs.SourceLocation;
import io.aeron.driver.MediaDriver;
import io.aeron.archive.client.AeronArchive;
import org.agrona.concurrent.UnsafeBuffer;

public class RecordingProducer {
    // Change from endpoint to explicit publish
    private static final String RECORDING_CHANNEL =
            "aeron:udp?endpoint=172.16.0.2:40456|control=172.16.0.5:40457|term-length=65536";  // Send TO archive, control FROM our IP
    private static final int MESSAGE_LENGTH = 32;
    private static final int STREAM_ID = 10;

    public static void main(String[] args) {
        MediaDriver.Context driverCtx = new MediaDriver.Context()
                .dirDeleteOnStart(true);

        try (MediaDriver driver = MediaDriver.launch(driverCtx)) {
            System.out.println("Recording Producer Media Driver started");

            // Connect to Archive
            AeronArchive.Context archiveCtx = new AeronArchive.Context()
                    .controlRequestChannel("aeron:udp?endpoint=172.16.0.2:8010")
                    .controlResponseChannel("aeron:udp?endpoint=172.16.0.5:8020");

            try (AeronArchive aeronArchive = AeronArchive.connect(archiveCtx)) {
                System.out.println("Connected to Archive");

                // Start Recording
                long recordingId = aeronArchive.startRecording(RECORDING_CHANNEL, STREAM_ID, SourceLocation.REMOTE);
                System.out.println("Started recording with ID: " + recordingId);

                // Create publication
                try (Publication publication = aeronArchive.context().aeron().addPublication(
                        RECORDING_CHANNEL, STREAM_ID)) {

                    System.out.println("Publication created, waiting for connection...");
                    while (!publication.isConnected()) {
                        Thread.sleep(100);
                        System.out.println("Waiting for publication connection...");
                    }

                    UnsafeBuffer buffer = new UnsafeBuffer(new byte[MESSAGE_LENGTH]);

                    int messageCount = 0;
                    while (true) {
                        String message = "Message " + messageCount++;
                        buffer.putStringWithoutLengthAscii(0, message);

                        long result;
                        while ((result = publication.offer(buffer, 0, MESSAGE_LENGTH)) < 0L) {
                            // Handle back pressure or other conditions
                            if (result == Publication.BACK_PRESSURED) {
                                System.out.println("Back pressured");
                                System.exit(1);
                            } else if (result == Publication.NOT_CONNECTED) {
                                System.out.println("Not connected");
                            } else if (result == Publication.ADMIN_ACTION) {
                                System.out.println("Admin action");
                            } else if (result == Publication.CLOSED) {
                                System.out.println("Publication closed");
                                break;
                            }
                            Thread.yield();
                        }
                        Thread.sleep(2);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}