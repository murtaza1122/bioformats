//
// NDPIReader.java
//

/*
OME Bio-Formats package for reading and converting biological file formats.
Copyright (C) 2005-@year@ UW-Madison LOCI and Glencoe Software, Inc.

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package loci.formats.in;

import java.io.IOException;

import loci.common.DateTools;
import loci.common.RandomAccessInputStream;
import loci.common.services.DependencyException;
import loci.common.services.ServiceFactory;
import loci.formats.CoreMetadata;
import loci.formats.FormatException;
import loci.formats.FormatTools;
import loci.formats.MissingLibraryException;
import loci.formats.meta.MetadataStore;
import loci.formats.services.JimiService;
import loci.formats.tiff.IFD;
import loci.formats.tiff.PhotoInterp;

/**
 * NDPIReader is the file format reader for Hamamatsu .ndpi files.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="http://trac.openmicroscopy.org.uk/ome/browser/bioformats.git/components/bio-formats/src/loci/formats/in/NDPIReader.java">Trac</a>,
 * <a href="http://git.openmicroscopy.org/?p=bioformats.git;a=blob;f=components/bio-formats/src/loci/formats/in/NDPIReader.java;hb=HEAD">Gitweb</a></dd></dl>
 */
public class NDPIReader extends BaseTiffReader {

  // -- Constants --

  private static final int MAX_SIZE = 8192;
  private static final int THUMB_TAG_1 = 65426;
  private static final int THUMB_TAG_2 = 65439;
  private static final int METADATA_TAG = 65449;

  // -- Fields --

  private JimiService service;
  private int initializedSeries = -1;
  private int initializedPlane = -1;

  private int sizeZ = 1;
  private int pyramidHeight = 1;

  // -- Constructor --

  /** Constructs a new NDPI reader. */
  public NDPIReader() {
    super("Hamamatsu NDPI", new String[] {"ndpi"});
    domains = new String[] {FormatTools.HISTOLOGY_DOMAIN};
    suffixNecessary = true;
  }

  // -- IFormatReader API methods --

  /**
   * @see loci.formats.IFormatReader#openBytes(int, byte[], int, int, int, int)
   */
  public byte[] openBytes(int no, byte[] buf, int x, int y, int w, int h)
    throws FormatException, IOException
  {
    FormatTools.checkPlaneParameters(this, no, buf.length, x, y, w, h);

    if (x == 0 && y == 0 && w == 1 && h == 1) {
      return buf;
    }
    else if (getSizeX() <= MAX_SIZE && getSizeY() <= MAX_SIZE) {
      int ifdIndex = getIFDIndex(getSeries(), no);
      return tiffParser.getSamples(ifds.get(ifdIndex), buf, x, y, w, h);
    }

    if (initializedSeries != getSeries() || initializedPlane != no) {
      if (x == 0 && y == 0 && w == getOptimalTileWidth() &&
        h == getOptimalTileHeight())
      {
        // it looks like we'll be reading lots of tiles
        setupService(0, 0, no);
      }
      else {
        // it looks like we'll only read one tile
        setupService(y, h, no);
      }
      initializedSeries = getSeries();
      initializedPlane = no;
    }
    else if (service.getScanline(y) == null) {
      setupService(y, h, no);
    }

    int c = getRGBChannelCount();
    int bytes = FormatTools.getBytesPerPixel(getPixelType());
    int row = w * c * bytes;

    for (int yy=y; yy<y + h; yy++) {
      byte[] scanline = service.getScanline(yy);
      if (scanline != null) {
        System.arraycopy(scanline, x * c * bytes, buf, (yy - y) * row, row);
      }
    }

    return buf;
  }

  /* @see loci.formats.IFormatReader#openThumbBytes(int) */
  public byte[] openThumbBytes(int no) throws FormatException, IOException {
    FormatTools.assertId(currentId, true, 1);

    int currentSeries = getSeries();
    if (currentSeries >= pyramidHeight) {
      return super.openThumbBytes(no);
    }

    setSeries(pyramidHeight - 1);
    byte[] thumb = FormatTools.openThumbBytes(this, no);
    setSeries(currentSeries);
    return thumb;
  }

  /* @see loci.formats.IFormatReader#close(boolean) */
  public void close(boolean fileOnly) throws IOException {
    super.close(fileOnly);
    if (!fileOnly) {
      if (service != null) {
        service.close();
      }
      service = null;
      initializedSeries = -1;
      initializedPlane = -1;
      sizeZ = 1;
      pyramidHeight = 1;
    }
  }

  /* @see loci.formats.IFormatReader#getOptimalTileHeight() */
  public int getOptimalTileHeight() {
    FormatTools.assertId(currentId, true, 1);
    int bpp = FormatTools.getBytesPerPixel(getPixelType());
    int maxHeight = (1024 * 1024) / (getSizeX() * getRGBChannelCount() * bpp);
    return (int) Math.min(maxHeight, getSizeY());
  }

  // -- Internal BaseTiffReader API methods --

  /* @see loci.formats.BaseTiffReader#initStandardMetadata() */
  protected void initStandardMetadata() throws FormatException, IOException {
    super.initStandardMetadata();

    try {
      ServiceFactory factory = new ServiceFactory();
      service = factory.getInstance(JimiService.class);
    }
    catch (DependencyException de) {
      throw new MissingLibraryException(
        "Could not find required Jimi library", de);
    }

    ifds = tiffParser.getIFDs();

    for (int i=1; i<ifds.size(); i++) {
      IFD ifd = ifds.get(i);
      if (ifd.getImageWidth() == ifds.get(0).getImageWidth() &&
        ifd.getImageLength() == ifds.get(0).getImageLength())
      {
        sizeZ++;
      }
      else if (sizeZ == 1) {
        pyramidHeight++;
      }
    }

    int seriesCount = pyramidHeight + (ifds.size() - pyramidHeight * sizeZ);
    core = new CoreMetadata[seriesCount];

    // repopulate core metadata

    for (int i=0; i<ifds.size(); i++) {
      IFD ifd = ifds.get(i);
      ifd.remove(THUMB_TAG_1);
      ifd.remove(THUMB_TAG_2);
      ifds.set(i, ifd);
      tiffParser.fillInIFD(ifds.get(i));
    }

    for (int s=0; s<core.length; s++) {
      setSeries(s);
      core[s] = new CoreMetadata();

      IFD ifd = ifds.get(getIFDIndex(s, 0));
      PhotoInterp p = ifd.getPhotometricInterpretation();
      int samples = ifd.getSamplesPerPixel();
      core[s].rgb = samples > 1 || p == PhotoInterp.RGB;

      core[s].sizeX = (int) ifd.getImageWidth();
      core[s].sizeY = (int) ifd.getImageLength();
      core[s].sizeZ = s < pyramidHeight ? sizeZ : 1;
      core[s].sizeT = 1;
      core[s].sizeC = core[s].rgb ? samples : 1;
      core[s].littleEndian = ifd.isLittleEndian();
      core[s].indexed = p == PhotoInterp.RGB_PALETTE &&
        (get8BitLookupTable() != null || get16BitLookupTable() != null);
      core[s].imageCount = getSizeZ() * getSizeT();
      core[s].pixelType = ifd.getPixelType();
      core[s].metadataComplete = true;
      core[s].interleaved = getSizeX() > MAX_SIZE || getSizeY() > MAX_SIZE;
      core[s].falseColor = false;
      core[s].dimensionOrder = "XYCZT";
      core[s].thumbnail = s != 0;
    }

    setSeries(0);
  }

  /* @see loci.formats.BaseTiffReader#initMetadataStore() */
  protected void initMetadataStore() throws FormatException {
    super.initMetadataStore();

    MetadataStore store = makeFilterMetadata();

    for (int i=0; i<getSeriesCount(); i++) {
      store.setImageName("Series " + (i + 1), i);

      if (i > 0) {
        int ifdIndex = getIFDIndex(i, 0);
        String creationDate = ifds.get(ifdIndex).getIFDTextValue(IFD.DATE_TIME);
        creationDate = DateTools.formatDate(creationDate, DATE_FORMATS);
        store.setImageAcquiredDate(creationDate, i);

        store.setPixelsPhysicalSizeX(ifds.get(ifdIndex).getXResolution(), i);
        store.setPixelsPhysicalSizeY(ifds.get(ifdIndex).getYResolution(), i);
        store.setPixelsPhysicalSizeZ(0.0, i);
      }
    }
  }

  // -- Helper methods --

  private void setupService(int y, int h, int z)
    throws FormatException, IOException
  {
    service.close();

    IFD ifd = ifds.get(getIFDIndex(getSeries(), z));

    long offset = ifd.getStripOffsets()[0];
    int byteCount = (int) ifd.getStripByteCounts()[0];
    if (in != null) {
      in.close();
    }
    in = new RandomAccessInputStream(currentId);
    in.seek(offset);
    in.setLength(offset + byteCount);

    service.initialize(in, y, h, getSizeX());
  }

  private int getIFDIndex(int seriesIndex, int zIndex) {
    if (seriesIndex < pyramidHeight) {
      return zIndex * pyramidHeight + seriesIndex;
    }

    return sizeZ * pyramidHeight + (seriesIndex - pyramidHeight);
  }

}