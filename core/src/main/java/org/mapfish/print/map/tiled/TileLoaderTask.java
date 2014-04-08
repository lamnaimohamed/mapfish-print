/*
 * Copyright (C) 2014  Camptocamp
 *
 * This file is part of MapFish Print
 *
 * MapFish Print is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MapFish Print is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MapFish Print.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.mapfish.print.map.tiled;

import com.google.common.collect.Lists;
import com.vividsolutions.jts.geom.Coordinate;
import jsr166y.ForkJoinTask;
import jsr166y.RecursiveTask;
import org.geotools.coverage.CoverageFactoryFinder;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridCoverageFactory;
import org.geotools.geometry.GeneralEnvelope;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.mapfish.print.attribute.map.MapBounds;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * @author Jesse on 4/3/14.
 */
public final class TileLoaderTask extends RecursiveTask<GridCoverage2D> {
    private static final Logger LOGGER = LoggerFactory.getLogger(TileLoaderTask.class);

    private final MapBounds bounds;
    private final Rectangle paintArea;
    private final double dpi;
    private final TileCacheInformation tiledLayer;
    private final BufferedImage errorImage;

    /**
     * Constructor.
     *
     * @param bounds        the map bounds
     * @param paintArea     the area to paint
     * @param dpi           the DPI to render at
     * @param tileCacheInfo the object used to create the tile requests
     */
    public TileLoaderTask(final MapBounds bounds, final Rectangle paintArea,
                          final double dpi, final TileCacheInformation tileCacheInfo) {
        this.bounds = bounds;
        this.paintArea = paintArea;
        this.dpi = dpi;
        this.tiledLayer = tileCacheInfo;
        final Dimension tileSize = this.tiledLayer.getTileSize();
        this.errorImage = new BufferedImage(tileSize.width, tileSize.height, BufferedImage.TYPE_4BYTE_ABGR);
        Graphics2D graphics = this.errorImage.createGraphics();
        try {
            // CSOFF:MagicNumber
            graphics.setBackground(new Color(255, 155, 155));
            // CSON:MagicNumber
            graphics.clearRect(0, 0, tileSize.width, tileSize.height);
        } finally {
            graphics.dispose();
        }
    }

    @Override
    protected GridCoverage2D compute() {
        try {
            final ReferencedEnvelope mapGeoBounds = this.bounds.toReferencedEnvelope(this.paintArea, this.dpi);
            final CoordinateReferenceSystem mapProjection = mapGeoBounds.getCoordinateReferenceSystem();
            Dimension tileSizeOnScreen = this.tiledLayer.getTileSize();

            final double layerResolution = this.tiledLayer.getScale().toResolution(mapProjection, this.tiledLayer.getLayerDpi());
            Coordinate tileSizeInWorld = new Coordinate(tileSizeOnScreen.width * layerResolution,
                    tileSizeOnScreen.height * layerResolution);

            // The minX minY of the first (minY,minY) tile
            Coordinate gridCoverageOrigin = this.tiledLayer.getMinGeoCoordinate(mapGeoBounds, tileSizeInWorld);

            URI commonUri = this.tiledLayer.createCommonURI();

            ReferencedEnvelope tileCacheBounds = this.tiledLayer.getTileCacheBounds();
            final double resolution = this.tiledLayer.getScale().toResolution(this.bounds.getProjection(), this.tiledLayer.getLayerDpi());
            double rowFactor = 1 / (resolution * tileSizeOnScreen.height);
            double columnFactor = 1 / (resolution * tileSizeOnScreen.width);

            int imageWidth = 0;
            int imageHeight = 0;
            int xIndex;
            int yIndex = (int) Math.floor((mapGeoBounds.getMaxY() - gridCoverageOrigin.y) / tileSizeInWorld.y) + 1;

            double gridCoverageMaxX = gridCoverageOrigin.x;
            double gridCoverageMaxY = gridCoverageOrigin.y;
            List<ForkJoinTask<Tile>> loaderTasks = Lists.newArrayList();

            for (double geoY = gridCoverageOrigin.y; geoY < mapGeoBounds.getMaxY(); geoY += tileSizeInWorld.y) {
                yIndex--;
                imageHeight += tileSizeOnScreen.height;
                imageWidth = 0;
                xIndex = -1;

                gridCoverageMaxX = gridCoverageOrigin.x;
                gridCoverageMaxY += tileSizeInWorld.y;
                for (double geoX = gridCoverageOrigin.x; geoX < mapGeoBounds.getMaxX(); geoX += tileSizeInWorld.x) {
                    xIndex++;
                    imageWidth += tileSizeOnScreen.width;
                    gridCoverageMaxX += tileSizeInWorld.x;

                    ReferencedEnvelope tileBounds = new ReferencedEnvelope(
                            geoX, geoX + tileSizeInWorld.x, geoY, geoY + tileSizeInWorld.y, mapProjection);

                    int row = (int) Math.round((tileCacheBounds.getMaxY() - tileBounds.getMaxY()) * rowFactor);
                    int column = (int) Math.round((tileBounds.getMinX() - tileCacheBounds.getMinX()) * columnFactor);

                    ClientHttpRequest tileRequest = this.tiledLayer.getTileRequest(commonUri, tileBounds, tileSizeOnScreen, column, row);

                    if (isVisible(tileCacheBounds, tileBounds)) {
                        final SingleTileLoaderTask task = new SingleTileLoaderTask(tileRequest, this.errorImage, xIndex, yIndex);
                        loaderTasks.add(task);
                    } else {
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("Tile out of bounds: " + tileRequest);
                        }
                        loaderTasks.add(new PlaceHolderImageTask(this.tiledLayer.getMissingTileImage(), xIndex, yIndex));
                    }
                }
            }

            BufferedImage coverageImage = this.tiledLayer.createBufferedImage(imageWidth, imageHeight);
            Graphics2D graphics = coverageImage.createGraphics();
            try {
                for (ForkJoinTask<Tile> loaderTask : loaderTasks) {
                    Tile tile = loaderTask.invoke();
                    if (tile.image != null) {
                        graphics.drawImage(tile.image, tile.xIndex * tileSizeOnScreen.width, tile.yIndex * tileSizeOnScreen.height, null);
                    }
                }
            } finally {
                graphics.dispose();
            }

            GridCoverageFactory factory = CoverageFactoryFinder.getGridCoverageFactory(null);
            GeneralEnvelope gridEnvelope = new GeneralEnvelope(mapProjection);
            gridEnvelope.setEnvelope(gridCoverageOrigin.x, gridCoverageOrigin.y, gridCoverageMaxX, gridCoverageMaxY);
            return factory.create(commonUri.toString(), coverageImage, gridEnvelope, null, null, null);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isVisible(final ReferencedEnvelope tileCacheBounds, final ReferencedEnvelope tileBounds) {
        final double boundsMinX = tileBounds.getMinX();
        final double boundsMinY = tileBounds.getMinY();
        return boundsMinX >= tileCacheBounds.getMinX() && boundsMinX <= tileCacheBounds.getMaxX()
               && boundsMinY >= tileCacheBounds.getMinY() && boundsMinY <= tileCacheBounds.getMaxY();
        //we don't use maxX and maxY since tilecache doesn't seems to care about those...
    }

    private static final class SingleTileLoaderTask extends RecursiveTask<Tile> {

        private final ClientHttpRequest tileRequest;
        private final int tileIndexX;
        private final int tileIndexY;
        private final BufferedImage errorImage;

        public SingleTileLoaderTask(final ClientHttpRequest tileRequest, final BufferedImage errorImage, final int tileIndexX,
                                    final int tileIndexY) {
            this.tileRequest = tileRequest;
            this.tileIndexX = tileIndexX;
            this.tileIndexY = tileIndexY;
            this.errorImage = errorImage;
        }

        @Override
        protected Tile compute() {
            ClientHttpResponse response = null;
            try {
                LOGGER.debug("\n\t" + this.tileRequest.getMethod() + " -- " + this.tileRequest.getURI());
                response = this.tileRequest.execute();
                final HttpStatus statusCode = response.getStatusCode();
                if (statusCode != HttpStatus.OK) {
                    LOGGER.error("Error making tile request: " + this.tileRequest.getURI() + "\n\tStatus: " + statusCode +
                                 "\n\tMessage: " + response.getStatusText());
                    return new Tile(this.errorImage, this.tileIndexX, this.tileIndexY);
                }

                BufferedImage image = ImageIO.read(response.getBody());

                return new Tile(image, this.tileIndexX, this.tileIndexY);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                if (response != null) {
                    response.close();
                }
            }
        }
    }

    private static class PlaceHolderImageTask extends RecursiveTask<Tile> {

        private final BufferedImage placeholderImage;
        private final int tileOriginX;
        private final int tileOriginY;

        public PlaceHolderImageTask(final BufferedImage placeholderImage, final int tileOriginX, final int tileOriginY) {
            this.placeholderImage = placeholderImage;
            this.tileOriginX = tileOriginX;
            this.tileOriginY = tileOriginY;
        }

        @Override
        protected Tile compute() {
            return new Tile(this.placeholderImage, this.tileOriginX, this.tileOriginY);
        }
    }

    private static final class Tile {
        /**
         * The tile image.
         */
        private final BufferedImage image;
        /**
         * The x index of the image.  the x coordinate to draw this tile is xIndex * tileSizeX
         */
        private final int xIndex;
        /**
         * The y index of the image.  the y coordinate to draw this tile is yIndex * tileSizeY
         */
        private final int yIndex;

        private Tile(final BufferedImage image, final int xIndex, final int yIndex) {
            this.image = image;
            this.xIndex = xIndex;
            this.yIndex = yIndex;
        }
    }
}
