//
// JimiService.java
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

package loci.formats.services;

import java.io.IOException;
import java.io.InputStream;

import loci.common.RandomAccessInputStream;
import loci.common.services.DependencyException;
import loci.common.services.Service;
import loci.common.services.ServiceException;

/**
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="http://trac.openmicroscopy.org.uk/ome/browser/bioformats.git/components/bio-formats/src/loci/formats/services/JimiService.java">Trac</a>,
 * <a href="http://git.openmicroscopy.org/?p=bioformats.git;a=blob;f=components/bio-formats/src/loci/formats/services/JimiService.java;hb=HEAD">Gitweb</a></dd></dl>
 *
 */
public interface JimiService extends Service {

  /** Initialize the given file. */
  public void initialize(String id, int imageWidth);

  /** Initialize the given file. */
  public void initialize(RandomAccessInputStream in, int imageWidth);

  /** Initialize the given file. */
  public void initialize(
    RandomAccessInputStream in, int y, int h, int imageWidth);

  /**
   * Return the scanline for row 'y' from the currently initialized file.
   */
  public byte[] getScanline(int y);

  /** Return the width of the image in the currently initialized file. */
  public int getWidth();

  /** Return the height of the image in the currently initialized file. */
  public int getHeight();

  /** Close the current file. */
  public void close();

}