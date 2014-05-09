package cs524t4.model;

import static cs524t4.support.Support.NEWLINE;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.DecimalFormat;

import cs524t4.datatype.A_LatitudeLongitude;
import cs524t4.datatype.Airspeed;
import cs524t4.datatype.Altitude;
import cs524t4.datatype.AngleNavigational;
import cs524t4.datatype.CoordinateWorld;
import cs524t4.datatype.CoordinateWorld3D;
import cs524t4.datatype.Latitude;
import cs524t4.datatype.Longitude;
import cs524t4.datatype.Scaler;
import cs524t4.datatype.Velocity;
import cs524t4.support.Support;

// ============================================================================================================================================================
/**
 * Defines a lookup table for a simple encoding that provides approximate wind velocity in knots through trilinear interpolation on discrete altitude planes
 * within a three-dimensional cell in the world. A cell is defined as a square with the size of one degree of latitude in a flat-earth world model. Its
 * bottom-right corner anchors to the world by the degree component of a latitude and longitude. Six stacked altitude planes define the velocities at 0, 3000,
 * 6000, 9000, 12000 feet, and 15000 feet. Altitudes outside this range clamp to the limits.
 * <p>
 * The definition resides in a separate text file for each cell. The format is as follows, where each dot grid is an altitude plane left to right in the order
 * indicated above. Rows are latitude, and columns are longitude, both on six-minute intervals offset from the anchor. For simplicity, the navigation model is
 * limited to the northern and western hemisphere, which means latitude is always degrees north and increases upward, and longitude is always degrees west and
 * increases leftward.
 * <p>
 * <tt>
 * <i>latitude_degrees</i>,<i>longitude_degrees</i><br>
 * ............ ............ ............ ............ ............ ............<br>
 * ............ ............ ............ ............ ............ ............<br>
 * ............ ............ ............ ............ ............ ............<br>
 * ............ ............ ............ ............ ............ ............<br>
 * ............ ............ ............ ............ ............ ............<br>
 * ............ ............ ............ ............ ............ ............<br>
 * ............ ............ ............ ............ ............ ............<br>
 * ............ ............ ............ ............ ............ ............<br>
 * ............ ............ ............ ............ ............ ............<br>
 * ............ ............ ............ ............ ............ ............<br>
 * ............ ............ ............ ............ ............ ............<br>
 * ............ ............ ............ ............ ............ ............<br>
 * </tt>
 * <p>
 * A dot indicates no wind. The encoding for the other discrete wind velocities is a single character that takes the place of a dot. It is a two-phase encoding
 * that combines both the direction and speed. The direction component is based on the eight cardinal and intercardinal directions with the following base
 * characters:
 * <p>
 * 
 * <pre>
 * Char  Direction
 * ----  ---------
 *  a         0
 *  f        45
 *  k        90
 *  p       135
 *  u       180
 *  z       225
 *  E       270
 *  J       315
 * </pre>
 * 
 * The speed component is alphabetically derived from the base character, which itself indicates 10 knots in the specified direction. Adding 1 to the character
 * (e.g., <tt>a+1=b</tt>) indicates 20 knots, whereas 2 is 30, 3 is 40, and 4 is 50. The complete encoding table is as follows:
 * 
 * <pre>
 * Char  Direction  Speed      Char  Direction  Speed
 * ----  ---------  -----      ----  ---------  -----
 *  .         0        0
 *  a         0       10        u       180       10
 *  b         0       20        v       180       20
 *  c         0       30        w       180       30
 *  d         0       40        x       180       40
 *  e         0       50        y       180       50
 * <br>
 *  f        45       10        z       225       10
 *  g        45       20        A       225       20
 *  h        45       30        B       225       30
 *  i        45       40        C       225       40
 *  j        45       50        D       225       50
 * <br>
 *  k        90       10        E       270       10
 *  l        90       20        F       270       20
 *  m        90       30        G       270       30
 *  n        90       40        H       270       40
 *  o        90       50        I       270       50
 * <br>
 *  p       135       10        J       315       10
 *  q       135       20        K       315       20
 *  r       135       30        L       315       30
 *  s       135       40        M       315       40
 *  t       135       50        N       315       50
 * </pre>
 * 
 * @author Dan Tappan [09.10.11]
 */
public class WindModel
{
   // ==========================================================================================================================================================
   /**
    * Defines a data wrapper for the row, column, and plane indices into the cell.
    * 
    * @author Dan Tappan [11.10.11]
    */
   private final class SubcellIndex
   {
      /** the row index on the interval [0, CELL_SIZE_HORIZONTAL) */
      int _indexRow = 0;

      /** the column index on the interval [0, CELL_SIZE_HORIZONTAL) */
      int _indexColumn = 0;

      /** the plane index on the interval [0, CELL_SIZE_VERTICAL) */
      int _indexPlane = 0;

      // ------------------------------------------------------------------------------------------------------------------------------------------------------
      /**
       * Creates a subcell index.
       * 
       * @param indexRow - the row index on the interval [0, <tt>CELL_SIZE_HORIZONTAL</tt>)
       * @param indexColumn - the column index on the interval [0, <tt>CELL_SIZE_HORIZONTAL</tt>)
       * @param indexPlane - the plane index on the interval [0, <tt>CELL_SIZE_VERTICAL</tt>)
       */
      public SubcellIndex(final int indexRow, final int indexColumn, final int indexPlane)
      {
         assert ((indexColumn >= 0) && (indexColumn < CELL_SIZE_HORIZONTAL)) : indexColumn;
         assert ((indexRow >= 0) && (indexRow < CELL_SIZE_HORIZONTAL)) : indexRow;
         assert ((indexPlane >= 0) && (indexPlane < CELL_SIZE_VERTICAL)) : indexPlane;

         _indexRow = indexRow;
         _indexColumn = indexColumn;
         _indexPlane = indexPlane;
      }

      // ------------------------------------------------------------------------------------------------------------------------------------------------------
      /**
       * {@inheritDoc}
       */
      @Override
      public String toString()
      {
         return ("row=" + _indexRow + " column=" + _indexColumn + " plane=" + _indexPlane);
      }
   }

   /** the horizontal size of the square cell, where each subcell corresponds to six degrees of latitude and latitude */
   private static final int CELL_SIZE_HORIZONTAL = 11;

   /** the vertical size of the cell, where the altitude layers are 0, 3000, 6000, 9000, 12000, and 15000 feet */
   private static final int CELL_SIZE_VERTICAL = 6;

   /** the span in latitude and longitude minutes represented by each subcell */
   private static final int MINUTES_PER_SUBCELL = (60 / (CELL_SIZE_HORIZONTAL - 1));

   /** the wind rates corresponding to base encoding plus 0, 1, 2, 3, and 4, respectively */
   private static final int[] WIND_RATES =
   { 10, 20, 30, 40, 50 };

   /** the altitude planes in feet */
   private static final int[] ALTITUDES =
   { 0, 3000, 6000, 9000, 12000, 15000 };

   /** the velocity encoding for no wind */
   private static final char ENCODING_NO_WIND = '.';

   /** the velocity encoding for wind */
   private static final String ENCODING_WIND = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMN";

   /** the subcell grid, as minutes of latitude, minutes of longitude, and altitude */
   private final Velocity[][][] _subcells = new Velocity[CELL_SIZE_HORIZONTAL][CELL_SIZE_HORIZONTAL][CELL_SIZE_VERTICAL];

   /** the bottom-right cell coordinate in degrees latitude and longitude. The minutes and seconds are always zero */
   private CoordinateWorld _cellAnchor;

   /** the fully qualified filename of the cell definition */
   private final String _filename;

   {
      assert (ENCODING_WIND.length() == (AngleNavigational.ANGLES.length * WIND_RATES.length)) : ENCODING_WIND.length();

      assert (ALTITUDES.length == CELL_SIZE_VERTICAL);
   }

   // ---------------------------------------------------------------------------------------------------------------------------------------------------------
   /**
    * Creates a wind-velocity cell.
    * 
    * @param filename - the fully qualified filename of the cell definition
    * 
    * @throws IOException for any file error
    */
   public WindModel(final String filename) throws IOException
   {
      assert (filename != null);

      _filename = filename;

      loadCellDefinition();
   }

   // ---------------------------------------------------------------------------------------------------------------------------------------------------------
   /**
    * Calculates the scaler between the two nearest latitude or longitude samples.
    * 
    * @param latitudeOrLongitude - the latitude or longitude
    * 
    * @return the scaler
    */
   private Scaler calculateScaler(final A_LatitudeLongitude<?> latitudeOrLongitude)
   {
      assert (latitudeOrLongitude != null);

      double fraction = (latitudeOrLongitude.getMinutesAndSeconds() / MINUTES_PER_SUBCELL);

      fraction = (1 - (fraction - (int) fraction));

      return new Scaler(fraction);
   }

   // ---------------------------------------------------------------------------------------------------------------------------------------------------------
   /**
    * Calculates the scaler between the two nearest altitude samples.
    * 
    * @param altitude - the altitude
    * 
    * @return the scaler
    */
   private Scaler calculateScaler(final Altitude altitude)
   {
      assert (altitude != null);

      double altitude2 = altitude.getValue_();

      if (altitude2 < ALTITUDES[0])
      {
         altitude2 = ALTITUDES[0];
      }
      else if (altitude2 > ALTITUDES[CELL_SIZE_VERTICAL - 1])
      {
         altitude2 = ALTITUDES[CELL_SIZE_VERTICAL - 1];
      }

      int index = mapPlaneIndex(altitude);

      int altitudeAbove = ALTITUDES[index + 1];
      int altitudeBelow = ALTITUDES[index];

      double fraction = ((altitude2 - altitudeBelow) / (altitudeAbove - altitudeBelow));

      return new Scaler(fraction);
   }

   // ---------------------------------------------------------------------------------------------------------------------------------------------------------
   /**
    * Decodes a character encoding into a velocity.
    * 
    * @param encoding - the encoding
    * 
    * @return the velocity
    */
   private Velocity decodeVelocity(final char encoding)
   {
      AngleNavigational direction;

      Airspeed speed;

      if (encoding == ENCODING_NO_WIND)
      {
         direction = AngleNavigational.ANGLE_000;

         speed = new Airspeed(0);
      }
      else
      {
         // determine the position of the encoding in the encoding list
         int encodingPosition = ENCODING_WIND.indexOf(encoding);

         if (encodingPosition == -1)
         {
            throw new RuntimeException("invalid encoding [" + encoding + "]");
         }

         // determine the direction
         int directionIndex = (encodingPosition / WIND_RATES.length);

         direction = AngleNavigational.ANGLES[directionIndex];

         // determine the speed
         int speedIndex = (encodingPosition - (directionIndex * WIND_RATES.length));

         speed = new Airspeed(WIND_RATES[speedIndex]);
      }

      return new Velocity(direction, speed);
   }

   // ---------------------------------------------------------------------------------------------------------------------------------------------------------
   /**
    * Generates a tabular representation of the wind definition, where the first value is direction in navigational degrees and the second speed in knots.
    * 
    * @return the representation
    */
   public StringBuilder dump()
   {
      DecimalFormat formatterSpeed = new DecimalFormat("00");

      StringBuilder stream = new StringBuilder();

      stream.append(_filename);
      stream.append(NEWLINE);
      stream.append(_cellAnchor);
      stream.append(NEWLINE);
      stream.append(NEWLINE);

      for (int iAltitude = 0; iAltitude < CELL_SIZE_VERTICAL; ++iAltitude)
      {
         stream.append("ALTITUDE ");
         stream.append(ALTITUDES[iAltitude]);
         stream.append(NEWLINE);

         for (int iLatitude = 0; iLatitude < CELL_SIZE_HORIZONTAL; ++iLatitude)
         {
            for (int iLongitude = 0; iLongitude < CELL_SIZE_HORIZONTAL; ++iLongitude)
            {
               Velocity velocity = _subcells[iLatitude][iLongitude][iAltitude];

               stream.append(velocity.getDirection().getValueFormatted());
               stream.append(':');
               stream.append(formatterSpeed.format(velocity.getSpeed().getValue_()));
               stream.append(" ");
            }

            stream.append(NEWLINE);
         }

         stream.append(NEWLINE);
      }

      return stream;
   }

   // ---------------------------------------------------------------------------------------------------------------------------------------------------------
   /**
    * Gets the bottom-right anchor coordinate of this cell. The minutes and seconds are always zero.
    * 
    * @return the coordinate
    */
   public CoordinateWorld getCellAnchor()
   {
      return _cellAnchor;
   }

   // ---------------------------------------------------------------------------------------------------------------------------------------------------------
   /**
    * Gets the fully qualified filename of the definition file for this cell.
    * 
    * @return the filename
    */
   public String getCellFilename()
   {
      return _filename;
   }

   // ---------------------------------------------------------------------------------------------------------------------------------------------------------
   /**
    * Esimates the wind velocity at a coordinate through three-dimension linear interpolation.
    * 
    * @param coordinate - the coordinate
    * 
    * @return the velocity
    * 
    * @exception UnsupportedCoordinateException if the coordinate is not on this cell
    */
   public Velocity interpolate(final CoordinateWorld3D coordinate) throws UnsupportedCoordinateException
   {
      assert (coordinate != null);

      validateCoordinate(coordinate.getCoordinateWorld());

      // do the horizontal interpolation
      Velocity velocityPlaneBelow = interpolatePlane(coordinate, true);
      Velocity velocityPlaneAbove = interpolatePlane(coordinate, false);

      // do the vertical interpolation
      Scaler scalerAltitude = calculateScaler(coordinate.getAltitude());

      return interpolateVelocity(velocityPlaneBelow, velocityPlaneAbove, scalerAltitude);
   }

   // ---------------------------------------------------------------------------------------------------------------------------------------------------------
   /**
    * Interpolates a coordinate by interpolating longitude at a latitude above and below a coordinate, then interpolating a latitude between these two results.
    * 
    * @param coordinate - the coordinate
    * @param isPlaneBelowOrAbovecoordinate - whether to use the altitude plane below or above the coordinate
    * 
    * @return the interpolated coordinate
    */
   private Velocity interpolatePlane(final CoordinateWorld3D coordinate, final boolean isPlaneBelowOrAbovecoordinate)
   {
      assert (coordinate != null);

      Scaler scalerLatitude = calculateScaler(coordinate.getLatitude());
      Scaler scalerLongitude = calculateScaler(coordinate.getLongitude());

      // box the coordinate
      SubcellIndex index = mapSubcellIndex(coordinate);

      int rowAbove = (index._indexRow - 1);
      int rowBelow = index._indexRow;

      int columnLeft = (index._indexColumn - 1);
      int columnRight = index._indexColumn;

      int plane = (isPlaneBelowOrAbovecoordinate ? index._indexPlane : (index._indexPlane + 1));

      // interpolate between the left and right samples of the row above the coordinate
      Velocity sampleRowAboveColumnLeft = _subcells[rowAbove][columnLeft][plane];
      Velocity sampleRowAboveColumnRight = _subcells[rowAbove][columnRight][plane];

      Velocity interpolationRowAbove = interpolateVelocity(sampleRowAboveColumnLeft, sampleRowAboveColumnRight, scalerLongitude);

      // interpolate between the left and right samples of the row below the coordinate
      Velocity sampleRowBelowColumnLeft = _subcells[rowBelow][columnLeft][plane];
      Velocity sampleRowBelowColumnRight = _subcells[rowBelow][columnRight][plane];

      Velocity interpolationRowBelow = interpolateVelocity(sampleRowBelowColumnLeft, sampleRowBelowColumnRight, scalerLongitude);

      // interpolate between the interpolations to estimate the horizontal value in the plane
      return interpolateVelocity(interpolationRowAbove, interpolationRowBelow, scalerLatitude);
   }

   // ---------------------------------------------------------------------------------------------------------------------------------------------------------
   /**
    * Interpolates between two velocities.
    * 
    * The rules are as follows:
    * <ul>
    * <li>if neither velocity has a speed, then the result has neither direction or speed; i.e., zero for each;
    * <li>if the first sample has a speed but the second doesn't, then take the first's direction and interpolate the speed between the two;
    * <li>if the second sample has a speed but the first doesn't, then take the second's direction and interpolate the speed between the two;
    * <li>otherwise, interpolate the direction and speed between the two samples.
    * </ul>
    * 
    * @param velocity1 - the first velocity
    * @param velocity2 - the second velocity
    * @param scaler - the scaler between the velocities
    * 
    * @return the interpolated velocity
    */
   private Velocity interpolateVelocity(final Velocity velocity1, final Velocity velocity2, final Scaler scaler)
   {
      assert (velocity1 != null);
      assert (velocity2 != null);
      assert (scaler != null);

      Velocity interpolation;

      Airspeed speed1 = velocity1.getSpeed();
      Airspeed speed2 = velocity2.getSpeed();

      boolean isSpeed1Zero = velocity1.hasSpeed();
      boolean isSpeed2Zero = velocity2.hasSpeed();

      if (isSpeed1Zero && isSpeed2Zero)
      {
         interpolation = new Velocity();
      }
      else
      {
         if (!isSpeed1Zero && isSpeed2Zero)
         {
            interpolation = new Velocity(velocity1.getDirection(), speed1.interpolate(speed2, scaler));
         }
         else if (isSpeed1Zero && !isSpeed2Zero)
         {
            interpolation = new Velocity(velocity2.getDirection(), speed1.interpolate(speed2, scaler));
         }
         else
         {
            interpolation = velocity1.interpolate(velocity2, scaler);
         }
      }

      return interpolation;
   }

   // ---------------------------------------------------------------------------------------------------------------------------------------------------------
   /**
    * Loads the cell definition from a file.
    * 
    * @throws IOException for any file error
    */
   private void loadCellDefinition() throws IOException
   {
      try (BufferedReader infile = new BufferedReader(new FileReader(_filename)))
      {
         loadCellDefinitionAnchor(infile);

         loadCellDefinitionBody(infile);
      }
   }

   // ---------------------------------------------------------------------------------------------------------------------------------------------------------
   /**
    * Loads the anchor component of the cell definition. This line specifies the latitude and longitude of the cell definition in this format:
    * <tt><i>latitude_degrees</i>,<i>longitude_degrees</i></tt>
    * 
    * @param infile - the input file
    * 
    * @throws IOException for any file error
    */
   private void loadCellDefinitionAnchor(final BufferedReader infile) throws IOException
   {
      assert (infile != null);

      final int INDEX_LATITUDE = 0;
      final int INDEX_LONGITUDE = 1;

      String inline = infile.readLine();

      if (inline == null)
      {
         throw new RuntimeException("invalid definition file " + _filename);
      }

      String[] anchorCoordinates = inline.split(",");

      if (anchorCoordinates.length != 2)
      {
         throw new RuntimeException("invalid anchor format [" + inline + "]; expected [latitude_degrees,longitude_degrees]");
      }

      try
      {
         int anchorLatitudeDegrees = Integer.parseInt(anchorCoordinates[INDEX_LATITUDE]);
         int anchorLongitudeDegrees = Integer.parseInt(anchorCoordinates[INDEX_LONGITUDE]);

         Latitude anchorLatitude = new Latitude(anchorLatitudeDegrees, 0, 0);
         Longitude anchorLongitude = new Longitude(anchorLongitudeDegrees, 0, 0);

         _cellAnchor = new CoordinateWorld(anchorLatitude, anchorLongitude);
      }
      catch (NumberFormatException exception)
      {
         throw new RuntimeException("invalid anchor format [" + inline + "]; expected [latitude_degrees,longitude_degrees]");
      }
   }

   // ---------------------------------------------------------------------------------------------------------------------------------------------------------
   /**
    * Loads the body component of the cell definition. These lines encode the wind velocity at the subcell coordinates.
    * 
    * @param infile - the input file
    * 
    * @throws IOException for any file error
    */
   private void loadCellDefinitionBody(final BufferedReader infile) throws IOException
   {
      assert (infile != null);

      int inlineLengthExpected = (((CELL_SIZE_HORIZONTAL + 1) * CELL_SIZE_VERTICAL) - 1);

      String inline;

      // iterate over each row, which is latitude across all altitude planes
      for (int iLatitude = 0; iLatitude < CELL_SIZE_HORIZONTAL; ++iLatitude)
      {
         inline = infile.readLine();

         if (inline == null)
         {
            throw new RuntimeException("invalid row count " + (iLatitude + 1) + "; expected " + CELL_SIZE_HORIZONTAL);
         }

         if (inline.length() != inlineLengthExpected)
         {
            throw new RuntimeException("invalid row " + (iLatitude + 1) + " length " + inline.length() + "; expected " + inlineLengthExpected);
         }

         int iChar = 0;

         char encoding;

         // iterate over each altitude plane in the current row
         for (int iAltitude = 0; iAltitude < CELL_SIZE_VERTICAL; ++iAltitude)
         {
            // iterate over each column in the current altitude plane, which is longitude
            for (int iLongitude = 0; iLongitude < CELL_SIZE_HORIZONTAL; ++iLongitude)
            {
               encoding = inline.charAt(iChar);

               _subcells[iLatitude][iLongitude][iAltitude] = decodeVelocity(encoding);

               ++iChar;
            }

            if (iChar < inlineLengthExpected)
            {
               encoding = inline.charAt(iChar);

               if (encoding != ' ')
               {
                  throw new RuntimeException("invalid altitude plane " + (iAltitude + 1) + " delimiter [" + encoding + "] in row " + (iLatitude + 1)
                        + "; expected space");
               }

               ++iChar;
            }
         }

         if (iChar != inlineLengthExpected)
         {
            throw new RuntimeException("invalid column count " + iChar + " in row " + (iLatitude + 1) + "; expected " + inlineLengthExpected);
         }
      }

      inline = infile.readLine();

      if (inline != null)
      {
         throw new RuntimeException("additional row(s) beyond expected " + CELL_SIZE_HORIZONTAL);
      }
   }

   // ---------------------------------------------------------------------------------------------------------------------------------------------------------
   /**
    * Determines which altitude plane on the supported interval lies below an altitude.
    * 
    * @param altitude - the altitude
    * 
    * @return the index
    */
   private int mapPlaneIndex(final Altitude altitude)
   {
      assert (altitude != null);

      int index = 0;

      int altitude2 = Support.round(altitude.getValue_());

      for (int iAltitude = (CELL_SIZE_VERTICAL - 2); iAltitude >= 0; --iAltitude)
      {
         if (altitude2 >= ALTITUDES[iAltitude])
         {
            index = iAltitude;

            break;
         }
      }

      assert ((index >= 0) && (index <= (CELL_SIZE_VERTICAL - 2))) : index;

      return index;
   }

   // ---------------------------------------------------------------------------------------------------------------------------------------------------------
   /**
    * Determines the row or column index in the subcell grid based on the minutes component of a latitude or longitude, respectively.
    * 
    * @param latitudeOrLongitude - the latitude or longitude
    * 
    * @return the index
    */
   private int mapRowColumnIndex(final A_LatitudeLongitude<?> latitudeOrLongitude)
   {
      assert (latitudeOrLongitude != null);

      int index = (CELL_SIZE_HORIZONTAL - (latitudeOrLongitude.getMinutes() / MINUTES_PER_SUBCELL) - 1);

      assert ((index >= 0) && (index < CELL_SIZE_HORIZONTAL)) : index;

      return index;
   }

   // ---------------------------------------------------------------------------------------------------------------------------------------------------------
   /**
    * Calculates the three-dimensional index in the subcell grid based on a coordinate with altitude.
    * 
    * @param coordinate - the coordinate
    * 
    * @return the subcell index
    */
   private SubcellIndex mapSubcellIndex(final CoordinateWorld3D coordinate)
   {
      assert (coordinate != null);

      Altitude altitude = coordinate.getAltitude();

      int indexRow = mapRowColumnIndex(coordinate.getLatitude());
      int indexColumn = mapRowColumnIndex(coordinate.getLongitude());
      int indexPlane = mapPlaneIndex(altitude);

      return new SubcellIndex(indexRow, indexColumn, indexPlane);
   }

   // ---------------------------------------------------------------------------------------------------------------------------------------------------------
   /**
    * {@inheritDoc}
    */
   @Override
   public String toString()
   {
      return ("coordinate=" + _cellAnchor + " filename=" + _filename);
   }

   // ---------------------------------------------------------------------------------------------------------------------------------------------------------
   /**
    * Validates that a coordinate resides on this cell.
    * 
    * @param coordinate - the coordinate
    * 
    * @exception UnsupportedCoordinateException if the coordinate is not on this cell
    */
   private void validateCoordinate(final CoordinateWorld coordinate) throws UnsupportedCoordinateException
   {
      assert (coordinate != null);

      boolean isValidLatitude = (coordinate.getLatitude().getDegrees() == _cellAnchor.getLatitude().getDegrees());
      boolean isValidLongitude = (coordinate.getLongitude().getDegrees() == _cellAnchor.getLongitude().getDegrees());

      if (!isValidLatitude || !isValidLongitude)
      {
         throw new UnsupportedCoordinateException("coordinate " + coordinate + " not on cell " + _cellAnchor);
      }
   }
}
