package org.opentripplanner.updater.traffic;

import com.beust.jcommander.internal.Maps;
import io.opentraffic.engine.data.pbf.ExchangeFormat;
import com.fasterxml.jackson.databind.JsonNode;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.traffic.Segment;
import org.opentripplanner.traffic.SegmentSpeedSample;
import org.opentripplanner.traffic.StreetSpeedSnapshot;
import org.opentripplanner.traffic.StreetSpeedSnapshotSource;
import org.opentripplanner.updater.GraphUpdaterManager;
import org.opentripplanner.updater.PollingGraphUpdater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Map;

/**
 * Update the graph with traffic data from OpenTraffic.
 */
public class OpenTrafficUpdater extends PollingGraphUpdater {
    private static final Logger LOG = LoggerFactory.getLogger(OpenTrafficUpdater.class);

    private Graph graph;
    private GraphUpdaterManager graphUpdaterManager;

    /** the tile directory to search through */
    private File tileDirectory;
    private double speed;

    private boolean hasAlreadyRun = false;

    @Override
    protected void runPolling() throws Exception {
        LOG.info("Loading speed data");

        // Build a speed index now while we're running in our own thread. We'll swap it out
        // at the appropriate time with a GraphWriterRunnable, but no need to synchronize yet.
        Map<Segment, SegmentSpeedSample> speedIndex = Maps.newHashMap();

        // Set the segment of the street where I live (way di, start node id, end node id)
        Segment myStreet = new Segment(354081300, 4532519951L, 267401672);
        Double avgSpeed = speed;
        int hoursInWeek = 24*7;
        double[] hoursBins =  new double[hoursInWeek]; // we need only average speed for tests
        for (int i = 0; i < hoursInWeek; i++) {
            hoursBins[i] = avgSpeed;
        }

        SegmentSpeedSample speedSample = new SegmentSpeedSample(avgSpeed, hoursBins);
        speedIndex.put(myStreet, speedSample);

        // Ignore the expected directory
        // search through the tile directory
        // for (File z : tileDirectory.listFiles()) {
        //     for (File x : z.listFiles()) {
        //         for (File y : x.listFiles()) {
        //             if (!y.getName().endsWith(".traffic.pbf")) {
        //                 LOG.warn("Skipping non-traffic file {} in tile directory", y);
        //                 continue;
        //             }

        //             // Deserialize it
        //             InputStream in = new BufferedInputStream(new FileInputStream(y));
        //             ExchangeFormat.BaselineTile tile = ExchangeFormat.BaselineTile.parseFrom(in);
        //             in.close();

        //             // TODO: handle metadata

        //             for (int i = 0; i < tile.getSegmentsCount(); i++) {
        //                 ExchangeFormat.BaselineStats stats = tile.getSegments(i);
        //                 SegmentSpeedSample sample;
        //                 try {
        //                     sample = new SegmentSpeedSample(stats);
        //                 } catch (IllegalArgumentException e) {
        //                     continue;
        //                 }
        //                 Segment segment = new Segment(stats.getSegment());
        //                 speedIndex.put(segment, sample);
        //             }
        //         }
        //     }
        // }

        LOG.info("Indexed {} speed samples", speedIndex.size()); // should be 1

        graphUpdaterManager.execute(graph -> {
            graph.streetSpeedSource.setSnapshot(new StreetSpeedSnapshot(speedIndex));
        });
    }

    @Override
    protected void configurePolling(Graph graph, JsonNode config) throws Exception {
        this.graph = graph;
        speed = config.get("speed").asDouble();
    }

    @Override
    public void setGraphUpdaterManager(GraphUpdaterManager updaterManager) {
        updaterManager.addUpdater(this);
        this.graphUpdaterManager = updaterManager;
    }

    @Override
    public void setup(Graph graph) {
        graph.streetSpeedSource = new StreetSpeedSnapshotSource();
    }

    @Override
    public void teardown() {
        graphUpdaterManager.execute(graph -> {
            graph.streetSpeedSource = null;
        });
    }
}
