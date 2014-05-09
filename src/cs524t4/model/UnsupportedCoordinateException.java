package cs524t4.model;

// ============================================================================================================================================================
/**
 * Defines an exception for a coordinate that is not supported in the defined world.
 * 
 * @author Dan Tappan [09.10.11]
 */
@SuppressWarnings("serial")
public class UnsupportedCoordinateException extends Exception
{
   // ---------------------------------------------------------------------------------------------------------------------------------------------------------
   /**
    * Creates an exception.
    */
   public UnsupportedCoordinateException()
   {
      //
   }

   // ---------------------------------------------------------------------------------------------------------------------------------------------------------
   /**
    * Creates an exception.
    * 
    * @param message - the message
    */
   public UnsupportedCoordinateException(final String message)
   {
      super(message);
   }

   // ---------------------------------------------------------------------------------------------------------------------------------------------------------
   /**
    * Creates an exception.
    * 
    * @param message - the message
    * @param cause - the cause
    */
   public UnsupportedCoordinateException(final String message, final Throwable cause)
   {
      super(message, cause);
   }

   // ---------------------------------------------------------------------------------------------------------------------------------------------------------
   /**
    * Creates an exception.
    * 
    * @param cause - the cause
    */
   public UnsupportedCoordinateException(final Throwable cause)
   {
      super(cause);
   }
}
