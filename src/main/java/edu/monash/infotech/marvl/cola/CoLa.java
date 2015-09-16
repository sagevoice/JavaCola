package edu.monash.infotech.marvl.cola;

public class CoLa {
    /**
     * provides an interface for use with any external graph system
     */
    public static LayoutAdaptor adaptor( final Options options ) {
        return new LayoutAdaptor( options );
    }
}
