package edu.monash.infotech.marvl.cola;

public class CoLa {
    public static LayoutAdaptor adaptor() {
        return new LayoutAdaptor();
    }
    /**
     * provides an interface for use with any external graph system
     */
    public static LayoutAdaptor adaptor( final Options options ) {
        return new LayoutAdaptor( options );
    }
}
