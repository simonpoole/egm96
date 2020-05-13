/*
 * Copyright (C) 2012 United States Government as represented by the Administrator of the National Aeronautics and Space
 * Administration. All Rights Reserved.
 */

package ch.poole.geo.egm96;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

/**
 * 
 * Computes EGM96 geoid offsets.
 * <p>
 * A file with the offset grid must be passed to the constructor. This file must have 721 rows of 1440 2-byte integer
 * values. Each row corresponding to a latitude, with the first row corresponding to +90 degrees (90 North). The integer
 * values must be in centimeters.
 * <p>
 * Once constructed, the instance can be passed to
 * {@link gov.nasa.worldwind.globes.EllipsoidalGlobe#applyEGMA96Offsets(String)} to apply the offsets to elevations
 * produced by the globe.
 *
 * @author tag
 * @version $Id: EGM96.java 770 2012-09-13 02:48:23Z tgaskins $
 */
public class EGM96 {
    private ShortBuffer deltas;

    /**
     * Construct a new instance using a file with the EGM96 data specified by a file system path
     * 
     * @param path the path to the file containing the EGM96 data
     * @throws IOException if the file can't be found or read
     */
    public EGM96(String path) throws IOException {
        loadOffsetFile(path);
    }

    /**
     * Construct a new instance using a file with the EGM96 data in resources
     * 
     * @throws IOException if the resource file can't be found or read
     */
    public EGM96() throws IOException {
        loadOffsetFileFromResource();
    }

    private void loadOffsetFile(String path) throws IOException {
        try (InputStream is = new FileInputStream(path)) {
            byte[] temp = new byte[is.available()];
            is.read(temp);
            ByteBuffer byteBuffer = ByteBuffer.wrap(temp);
            deltas = ((ByteBuffer) (byteBuffer.rewind())).asShortBuffer();
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        }

    }

    private void loadOffsetFileFromResource() throws IOException {
        ClassLoader classloader = Thread.currentThread().getContextClassLoader();

        try (InputStream is = classloader.getResourceAsStream("EGM96.dat")) {
            if (is == null) {
                throw new IOException("Didn't find resource EGM96.dat");
            }
            byte[] temp = new byte[is.available()];
            is.read(temp);
            ByteBuffer byteBuffer = ByteBuffer.wrap(temp);
            deltas = ((ByteBuffer) (byteBuffer.rewind())).asShortBuffer();
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        }

    }

    // Description of the EGMA96 offsets file:
    // See http://earth-info.nga.mil/GandG/wgs84/gravitymod/egm96/binary/binarygeoid.html
    // The total size of the file is 2,076,480 bytes. This file was created
    // using an INTEGER*2 data type format and is an unformatted direct access
    // file. The data on the file is arranged in records from north to south.
    // There are 721 records on the file starting with record 1 at 90 N. The
    // last record on the file is at latitude 90 S. For each record, there
    // are 1,440 15 arc-minute geoid heights arranged by longitude from west to
    // east starting at the Prime Meridian (0 E) and ending 15 arc-minutes west
    // of the Prime Meridian (359.75 E). On file, the geoid heights are in units
    // of centimeters. While retrieving the Integer*2 values on file, divide by
    // 100 and this will produce a geoid height in meters.

    private static final double INTERVAL = 15d / 60d; // 15' angle delta
    private static final int    NUM_ROWS = 721;
    private static final int    NUM_COLS = 1440;

    public double getOffset(double lat, double lon) {

        // Return 0 for all offsets if the file failed to load. A log message of the failure will have been generated
        // by the load method.
        if (this.deltas == null)
            return 0;

        lon = lon >= 0 ? lon : lon + 360;

        int topRow = (int) ((90 - lat) / INTERVAL);
        if (lat <= -90) {
            topRow = NUM_ROWS - 2;
        }
        int bottomRow = topRow + 1;

        // Note that the number of columns does not repeat the column at 0 longitude, so we must force the right
        // column to 0 for any longitude that's less than one interval from 360, and force the left column to the
        // last column of the grid.
        int leftCol = (int) (lon / INTERVAL);
        int rightCol = leftCol + 1;
        if (lon >= 360 - INTERVAL) {
            leftCol = NUM_COLS - 1;
            rightCol = 0;
        }

        double latBottom = 90 - bottomRow * INTERVAL;
        double lonLeft = leftCol * INTERVAL;

        try {
            double ul = this.gePostOffset(topRow, leftCol);
            double ll = this.gePostOffset(bottomRow, leftCol);
            double lr = this.gePostOffset(bottomRow, rightCol);
            double ur = this.gePostOffset(topRow, rightCol);

            double u = (lon - lonLeft) / INTERVAL;
            double v = (lat - latBottom) / INTERVAL;

            double pll = (1.0 - u) * (1.0 - v);
            double plr = u * (1.0 - v);
            double pur = u * v;
            double pul = (1.0 - u) * v;

            double offset = pll * ll + plr * lr + pur * ur + pul * ul;

            return offset / 100d; // convert centimeters to meters
        } catch (IllegalArgumentException iaex) {
            return 0;
        }
    }

    private double gePostOffset(int row, int col) {
        int k = row * NUM_COLS + col;
        if (k >= this.deltas.limit()) {
            throw new IllegalArgumentException("row " + row + " col " + col + " out of range");
        }
        return this.deltas.get(k);
    }
}
