package com.freeipodsoftware.abc.conversionstrategy;

import com.freeipodsoftware.abc.Messages;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Header;
import org.eclipse.swt.widgets.Shell;
import uk.yermak.audiobookconverter.FFMpegFaacConverter;
import uk.yermak.audiobookconverter.Mp4v2Tagger;
import uk.yermak.audiobookconverter.Tagger;

import java.io.*;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class JoiningConversionStrategy extends AbstractConversionStrategy implements Runnable {
    private String outputFileName;
    private int currentFileNumber;
    private int channels = 2;
    private int frequency = 44100;
    private int bitrate = 128000;
    private long duration;

    public JoiningConversionStrategy() {
    }

    public long getOutputSize() {
        return this.canceled ? 0L : (new File(this.outputFileName)).length();
    }

    public int calcPercentFinishedForCurrentOutputFile() {
        return this.getProgress();
    }

    public String getInfoText() {
        return Messages.getString("JoiningConversionStrategy.file") + " " + this.currentFileNumber + "/" + this.inputFileList.length;
    }

    public boolean makeUserInterview(Shell shell) {
        this.outputFileName = selectOutputFile(shell, this.getOuputFilenameSuggestion(this.inputFileList));
        return this.outputFileName != null;
    }

    protected void startConversion() {
        Executors.newWorkStealingPool().execute(this);
    }

    public void run() {
        try {
            this.determineMaxChannelsAndFrequency();

            Future converterFuture =
                    Executors.newWorkStealingPool()
                            .submit(new FFMpegFaacConverter(bitrate, channels, frequency, duration, outputFileName, inputFileList));

            converterFuture.get();

            Tagger tagger = new Mp4v2Tagger(mp4Tags, outputFileName);
            tagger.tagIt();

//            ChapterBuilder chapterBuilder = new Mp4v2ChapterBuilder(futures, outputFileName);
//            chapterBuilder.chapters();

        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            this.finishListener.finishedWithError(e.getMessage() + "; " + sw.getBuffer().toString());
        } finally {
            this.percentFinished = 100;
            this.finished = true;
            this.finishListener.finished();
        }
    }

    private void determineMaxChannelsAndFrequency() {
        this.channels = 0;
        this.frequency = 0;

        try {
            for (String inputFile : this.inputFileList) {
                BufferedInputStream sourceStream = new BufferedInputStream(new FileInputStream(inputFile));
                Bitstream stream = new Bitstream(sourceStream);
                Header header = stream.readFrame();
                int fileChannels = header.mode() == 3 ? 1 : 2;
                if (fileChannels > this.channels) {
                    this.channels = fileChannels;
                }

                int fileFrequency = header.frequency();
                if (fileFrequency > this.frequency) {
                    this.frequency = fileFrequency;
                }

                int fileBitrate = header.bitrate();
                if (fileBitrate > this.bitrate) {
                    this.bitrate = header.bitrate();
                }

                stream.close();
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String getAdditionalFinishedMessage() {
        return Messages.getString("JoiningConversionStrategy.outputFilename") + ":\n" + this.outputFileName;
    }
}