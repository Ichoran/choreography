/* DataReadyListener.java - Listener for DataReady events.
 * Copyright 2010 Howard Hughes Medical Institute and Nicholas Andrew Swierczek
 * Also copyright 2015 Calico Life Sciences LLC (authored by Rex Kerr)
 * This file is a part of Choreography and is distributed under the
 * terms of the GNU Lesser General Public Licence version 2.1 (LGPL 2.1).
 * For details, see http://www.gnu.org/licences
 */

package mwt;

import java.util.EventListener;

/**
 * An interface which defines a listener that allows a {@code {@link DataMapSource}} to
 * notify implementing classes when a previously requested view of the data is available.
 * @author Nicholas Andrew Swierczek (swierczekn at janelia dot hhmi dot org)
 */
public interface DataReadyListener extends EventListener {
    /**
     * Invoked when a {@code DataMapSource} has completed generating a new view.
     */
    public void dataReady(DataReadyEvent e);
}
