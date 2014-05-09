package cs524t4.model;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

import cs524t4.datatype.Altitude;
import cs524t4.datatype.CoordinateCartesianRelative;
import cs524t4.datatype.CoordinateWorld3D;
import cs524t4.datatype.Latitude;
import cs524t4.datatype.Longitude;
import cs524t4.datatype.Velocity;
import cs524t4.support.Assert;

//============================================================================================================================================================
/**
 * Serves as a basic driver for the CS 524 Task 4 wind model.
 * 
 * @author Dan Tappan [30.04.14]
 */
@SuppressWarnings("all")
public class WindModelDriver
{
   // ---------------------------------------------------------------------------------------------------------------------------------------------------------
   /**
    * Runs the driver.
    * 
    * @param arguments - two command-line arguments: the fully qualified filename of the input file as specified in {@link cs524t4.model.WindModel}; the path
    * name where the output files will be written
    * 
    * @throws Exception if anything fails
    */
   public static void main(final String[] arguments) throws Exception
   {
      if (arguments.length != 2)
      {
         throw new RuntimeException("usage: <filespecIn> <filepathOut>");
      }

      WindModelDriver driver = new WindModelDriver();

      driver.generateGnuplotOutput(arguments[0], arguments[1]);
   }

   // ---------------------------------------------------------------------------------------------------------------------------------------------------------
   /**
    * Generates the intermediate Gnuplot output data file for each interpolated altitude based on a wind description, and generates the Gnuplot output script
    * file for combining them into an animated GIF.
    * 
    * @param filespecIn - the fully qualified filename of the input file as specified in {@link cs524t4.model.WindModel}
    * @param filepathOut - the path name where the output files will be written
    * 
    * @throws Exception if anything fails
    */
   public void generateGnuplotOutput(final String filespecIn, final String filepathOut) throws Exception
   {
      Assert.nonnullempty(filespecIn, filepathOut);

      final int animationDelay = 10; // milliseconds; you may change this

      final int altitudeMin = 0;
      final int altitudeMax = 15000;
      final int altitudeStep = 100;

      // generate the dataset from each iteration and build the script to animate it in Gnuplot
      StringBuilder script = new StringBuilder();

      script.append("\n");
      script.append("reset\n");
      script.append("set xrange [16:15]\n");
      script.append("set terminal gif animate delay " + animationDelay + "\n");
      script.append("set output 'wind.gif'\n");

      System.out.println("generating from " + filespecIn);

      WindModel cell = new WindModel(filespecIn);

      String filespecOutScript = (filepathOut + File.separatorChar + "script.gnu");

      BufferedWriter outfileScript = new BufferedWriter(new FileWriter(filespecOutScript));

      for (int iAltitude = altitudeMin; iAltitude <= altitudeMax; iAltitude += altitudeStep)
      {
         Altitude altitude = new Altitude(iAltitude);

         String filespecOutData = (filepathOut + File.separatorChar + "gnuplot_" + iAltitude + ".txt");

         System.out.print(" altitude " + iAltitude + " as " + filespecOutData + "...");

         BufferedWriter outfileData = new BufferedWriter(new FileWriter(filespecOutData));

         script.append("print 'generating " + filespecOutData + "'\n");
         script.append("plot '" + filespecOutData + "' using 1:2:3:4 with vectors head filled lt 1\n");

         int secondsStep = 60;

         for (int iLatitudeMinutes = 0; iLatitudeMinutes <= 59; ++iLatitudeMinutes)
         {
            for (int iLatitudeSeconds = 0; iLatitudeSeconds <= 59; iLatitudeSeconds += secondsStep)
            {
               Latitude latitude = new Latitude(45, iLatitudeMinutes, iLatitudeSeconds);

               for (int iLongitudeMinutes = 0; iLongitudeMinutes <= 59; ++iLongitudeMinutes)
               {
                  for (int iLongitudeSeconds = 0; iLongitudeSeconds <= 59; iLongitudeSeconds += secondsStep)
                  {
                     Longitude longitude = new Longitude(15, iLongitudeMinutes, iLongitudeSeconds);

                     CoordinateWorld3D coordinate = new CoordinateWorld3D(latitude, longitude, altitude);

                     Velocity velocity = cell.interpolate(coordinate);

                     CoordinateCartesianRelative deltas = velocity.generateDeltas();

                     outfileData.write(coordinate.getLongitude().getValue_() + " " + coordinate.getLatitude().getValue_() + " " + (deltas.getX() / 3600) + " "
                           + (deltas.getY() / 3600) + System.lineSeparator());
                  }
               }
            }
         }

         outfileData.close();

         System.out.println("ok");
      }

      outfileScript.write(script.toString());
      outfileScript.close();

      System.out.println("done");
   }
}
