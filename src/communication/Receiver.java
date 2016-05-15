package communication;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;

import common.Coord;
import enums.Science;
import enums.Terrain;

/** Read incoming messages from other ROVERS. Parse the data according to the
 * settings. Used to stored all the shared science coordinates
 * 
 * @author Shay */
public interface Receiver {

    /** @return A list of terrains you want to ignored or "filtered" from
     *         incoming message from other ROVERS */
    List<Terrain> getIgnoredTerrains();

    /** @return A list of sciences you want to ignored or "filtered" out from
     *         incoming message from other ROVERS */
    List<Science> getPreferedSciences();

    /** @return A list of all the coordinates sent to you from other ROVERS that
     *         did not get "filtered" out */
    List<Coord> getSharedCoords();

    /** @param terrains
     *            Ignored all incoming result from other ROVERS that is on these
     *            Terrains. For example, Walkers would want to ignore SAND */
    void ignoreTerrains(Terrain... terrains);

    /** @param sciences
     *            All the science you want to collect. The science you want to
     *            filter out from incoming messages from other ROVERS */
    void acceptSciences(Science... sciences);

    /** Start ROVER server, accept incoming message from other ROVERS
     * 
     * @param serverSocket
     * @throws IOException */
    void startServer(ServerSocket serverSocket) throws IOException;
    
    int getRoversConnectedToMe();

}
