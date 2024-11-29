package org.starquake.archive;

import io.aeron.Aeron;
import io.aeron.archive.client.AeronArchive;
import org.agrona.concurrent.status.CountersReader;

public class ArchiveMonitor {
    private final Aeron aeron;
    private final AeronArchive aeronArchive;
    private final long monitoringIntervalMs = 5000; // 5-second reporting interval
    private long nextReportTimeMs;

    public ArchiveMonitor(Aeron aeron, AeronArchive aeronArchive) {
        this.aeron = aeron;
        this.aeronArchive = aeronArchive;
        this.nextReportTimeMs = System.currentTimeMillis();
    }

    private static class ReplayProgress {
        long lastBytesRead;
        long lastUpdateTime;
    }

    public void doWork() {
        final long now = System.currentTimeMillis();
        if (now >= nextReportTimeMs) {
            generateReport();
            nextReportTimeMs = now + monitoringIntervalMs;

            // Get the active recording ID
            long recordingId = getActiveRecordingId();
            if (recordingId != -1) {
                long purgePosition = calculatePurgePosition(recordingId);
                if (purgePosition > 0) {
                    attemptSegmentPurge(recordingId, purgePosition);
                } else {
                    System.out.println("A: Not enough data to purge yet.");
                }
            } else {
                System.out.println("Skipping purge as there is no active recording.");
            }
        }
    }




    private void generateReport() {
        System.out.printf("%n=== Archive Monitor Report ===%n");

        final CountersReader countersReader = aeron.countersReader();
        int activeReplayCount = 0;



        for (int counterId = 0; counterId < countersReader.maxCounterId(); counterId++) {
            int counterState = countersReader.getCounterState(counterId);
            if (counterState == CountersReader.RECORD_ALLOCATED) {
                final int typeId = countersReader.getCounterTypeId(counterId);
                final long value = countersReader.getCounterValue(counterId);
                final String label = countersReader.getCounterLabel(counterId);

                System.out.printf("Label: %s, typeId: %d, value: %d%n", label, typeId, value);

//                // Check for the "Archive Replay Sessions" counter
//                if (typeId == 112 && label.contains("Archive Replay Sessions")) {
//                    activeReplayCount = (int) value;
//                    System.out.printf("Detected Active Replay Sessions Counter - Counter ID: %d, Value: %d%n",
//                            counterId, value);
//                }
            }
        }

        System.out.printf("Total Active Replays Detected: %d%n", activeReplayCount);
        System.out.println("============================");
    }

    public void attemptSegmentPurge(long recordingId, long purgePosition) {
        try {
            // Attempt to purge segments up to purgePosition
            aeronArchive.purgeSegments(recordingId, purgePosition);
            System.out.printf("Successfully purged segments up to position %d for recording %d%n",
                    purgePosition, recordingId);
        } catch (Exception e) {
            String errorMessage = e.getMessage();
            System.err.printf("Failed to purge segments for recording %d due to: %s%n",
                    recordingId, errorMessage);
            if (errorMessage.contains("because active replay")) {
                // Extract replaySessionId from the exception message
                Long replaySessionId = extractReplaySessionIdFromException(errorMessage);
                if (replaySessionId != null) {
                    System.out.printf("Stopping interfering replay - Session ID: %d%n", replaySessionId);
                    try {
                        aeronArchive.stopReplay(replaySessionId);
                        // Retry the purge after stopping the interfering replay
                        attemptSegmentPurge(recordingId, purgePosition);
                    } catch (Exception stopReplayException) {
                        System.err.printf("Failed to stop replay session %d: %s%n",
                                replaySessionId, stopReplayException.getMessage());
                    }
                } else {
                    System.err.println("Could not extract replaySessionId from exception message.");
                }
            } else {
                // Handle other exceptions as needed
                e.printStackTrace();
            }
        }
    }

    private Long extractReplaySessionIdFromException(String errorMessage) {
        System.out.printf("Parsing error message %s%n", errorMessage);
        // Example exception message format:
        // "cannot purge recording segments for recordingId=12345 and segment file position=1000000 because active replay replaySessionId=67890 is in progress"
        try {
            String marker = "replaySessionId=";
            int index = errorMessage.indexOf(marker);
            if (index != -1) {
                int endIndex = errorMessage.indexOf(' ', index);
                if (endIndex == -1) {
                    endIndex = errorMessage.length();
                }
                String replaySessionIdStr = errorMessage.substring(index + marker.length(), endIndex);
                return Long.parseLong(replaySessionIdStr);
            }
        } catch (Exception e) {
            // Handle parsing exceptions
            System.err.println("Error parsing replaySessionId from exception message: " + e.getMessage());
        }
        return null;
    }

    private long calculatePurgePosition(long recordingId) {
        final long[] startPositionHolder = new long[1];
        final long[] segmentFileLengthHolder = new long[1];

        // Retrieve recording details
        aeronArchive.listRecording(recordingId,
                (controlSessionId, correlationId, recId, startTimestamp, stopTimestamp,
                 startPosition, stopPosition, initialTermId, segmentFileLength,
                 termBufferLength, mtuLength, sessionId, streamId, strippedChannel,
                 originalChannel, sourceIdentity) -> {
                    startPositionHolder[0] = startPosition;
                    segmentFileLengthHolder[0] = segmentFileLength;
                });

        long startPosition = startPositionHolder[0];
        long segmentFileLength = segmentFileLengthHolder[0];

        // Get the current position of the recording
        long currentPosition = aeronArchive.getRecordingPosition(recordingId);
        if (currentPosition == Aeron.NULL_VALUE) {
            // Recording is not active; use stopPosition instead
            final long[] stopPositionHolder = new long[1];
            aeronArchive.listRecording(recordingId,
                    (controlSessionId, correlationId, recId, startTimestamp, stopTimestamp,
                     sPosition, ePosition, initialTermId, sFileLength,
                     tBufferLength, mLength, sId, sIdStream, sChannel,
                     oChannel, sIdentity) -> {
                        stopPositionHolder[0] = ePosition;
                    });
            currentPosition = stopPositionHolder[0];

            if (currentPosition == Aeron.NULL_VALUE || currentPosition == -1) {
                // No data recorded yet
                System.out.println("Recording has no data.");
                return -1;
            }
        }
        System.out.printf("(seg length) %d, Start position: %d%n",segmentFileLength, startPosition);
        System.out.printf("Current position: %d%n", currentPosition);

        // Calculate the number of complete segments recorded
        long recordedLength = currentPosition - startPosition;
        long segmentsRecorded = recordedLength / segmentFileLength;

        System.out.printf("Segments recorded: %d%n", segmentsRecorded);

        System.out.printf("Segments recorded: %d%n", segmentsRecorded);
        // Decide how many segments to purge (e.g., purge all but the last segment)
        long segmentsToPurge = segmentsRecorded - 1; // Keep the last segment to avoid purging data currently being written

        if (segmentsToPurge > 0) {
            long purgePosition = startPosition + (segmentsToPurge * segmentFileLength);
            System.out.printf("Calculated purge position: %d%n", purgePosition);
            return purgePosition;
        } else {
            System.out.println("B: Not enough data to purge yet.");
            return -1;
        }
    }



    private long getRecordingId() {
        // Implement logic to retrieve the recording ID
        // For simplicity, let's list recordings and pick the one we want
        final long[] recordingIdHolder = new long[1];
        aeronArchive.listRecordings(
                0, Integer.MAX_VALUE,
                (controlSessionId, correlationId, recordingId, startTimestamp, stopTimestamp,
                 startPosition, stopPosition, initialTermId, segmentFileLength,
                 termBufferLength, mtuLength, sessionId, streamId, strippedChannel,
                 originalChannel, sourceIdentity) -> {
                    // For example, pick the first recording
                    recordingIdHolder[0] = recordingId;
                });
        return recordingIdHolder[0];
    }

    private long getActiveRecordingId() {
        final long[] activeRecordingIdHolder = new long[1];
        aeronArchive.listRecordings(
                0, Integer.MAX_VALUE,
                (controlSessionId, correlationId, recordingId, startTimestamp, stopTimestamp,
                 startPosition, stopPosition, initialTermId, segmentFileLength,
                 termBufferLength, mtuLength, sessionId, streamId, strippedChannel,
                 originalChannel, sourceIdentity) -> {
                    // Check if the recording is active (stopPosition == -1)
                    if (stopPosition == Aeron.NULL_VALUE || stopPosition == -1) {
                        activeRecordingIdHolder[0] = recordingId;
                    }
                });
        if (activeRecordingIdHolder[0] != 0) {
            return activeRecordingIdHolder[0];
        } else {
            System.out.println("No active recording found.");
            return -1;
        }
    }


}
