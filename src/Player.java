import com.mpatric.mp3agic.InvalidDataException;
import com.mpatric.mp3agic.UnsupportedTagException;
import javazoom.jl.decoder.*;
import javazoom.jl.player.AudioDevice;
import support.PlayerWindow;
import support.Song;

import javax.swing.event.MouseInputAdapter;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.Arrays;

public class Player {

    private static final int SIZE = 1000;
    private static final int INFO_SIZE = 6;

    //Program name
    private static final String TITULO_DA_JANELA = "mp3";
    //playlist with [row][column]
    private String[][] LISTA_DE_REPRODUÇÃO = new String[SIZE][INFO_SIZE];

    /**
     * The MPEG audio bitstream.
     */
    private Bitstream bitstream;
    /**
     * The MPEG audio decoder.
     */
    private Decoder decoder;
    /**
     * The AudioDevice where audio samples are written to.
     */
    private AudioDevice device;

    private PlayerWindow window;

    private int currentFrame = 0;

    private final ActionListener buttonListenerPlayNow = e ->System.out.println(e);
    private final ActionListener buttonListenerRemove = e ->System.out.println(e) ;
    private final ActionListener buttonListenerAddSong = e ->addQueue() ;
    private final ActionListener buttonListenerPlayPause = e ->System.out.println(e) ;
    private final ActionListener buttonListenerStop = e ->System.out.println(e) ;
    private final ActionListener buttonListenerNext = e ->System.out.println(e) ;
    private final ActionListener buttonListenerPrevious = e -> System.out.println(e);
    private final ActionListener buttonListenerShuffle = e -> System.out.println(e);
    private final ActionListener buttonListenerLoop = e ->System.out.println(e) ;
    private final MouseInputAdapter scrubberMouseInputAdapter = new MouseInputAdapter() {
        @Override
        public void mouseReleased(MouseEvent e) {
        }

        @Override
        public void mousePressed(MouseEvent e) {
        }

        @Override
        public void mouseDragged(MouseEvent e) {
        }
    };

    public Player() {
        EventQueue.invokeLater(() -> window = new PlayerWindow(
                TITULO_DA_JANELA,
                LISTA_DE_REPRODUÇÃO,
                buttonListenerPlayNow,
                buttonListenerRemove,
                buttonListenerAddSong,
                buttonListenerShuffle,
                buttonListenerPrevious,
                buttonListenerPlayPause,
                buttonListenerStop,
                buttonListenerNext,
                buttonListenerLoop,
                scrubberMouseInputAdapter)
        );
    }

    //<editor-fold desc="Essential">

    /**
     * @return False if there are no more frames to play.
     */
    private boolean playNextFrame() throws JavaLayerException {
        // TODO Is this thread safe?
        if (device != null) {
            Header h = bitstream.readFrame();
            if (h == null) return false;

            SampleBuffer output = (SampleBuffer) decoder.decodeFrame(h, bitstream);
            device.write(output.getBuffer(), 0, output.getBufferLength());
            bitstream.closeFrame();
        }
        return true;
    }

    /**
     * @return False if there are no more frames to skip.
     */
    private boolean skipNextFrame() throws BitstreamException {
        // TODO Is this thread safe?
        Header h = bitstream.readFrame();
        if (h == null) return false;
        bitstream.closeFrame();
        currentFrame++;
        return true;
    }

    /**
     * Skips bitstream to the target frame if the new frame is higher than the current one.
     *
     * @param newFrame Frame to skip to.
     * @throws BitstreamException Generic Bitstream exception.
     */
    private void skipToFrame(int newFrame) throws BitstreamException {
        // TODO Is this thread safe?
        if (newFrame > currentFrame) {
            int framesToSkip = newFrame - currentFrame;
            boolean condition = true;
            while (framesToSkip-- > 0 && condition) condition = skipNextFrame();
        }
    }
    //</editor-fold>

    //Add music to playlist
    public void addQueue(){
        try{
            Song song = window.openFileChooser();

            if(song != null){
                System.arraycopy(song.getDisplayInfo(), 0,LISTA_DE_REPRODUÇÃO[findEmpty()] , 0, INFO_SIZE);
                window.setQueueList(LISTA_DE_REPRODUÇÃO);
            }

        }catch (InvalidDataException | BitstreamException | UnsupportedTagException | IOException exception){
            System.out.println(exception);
        }
    }

    //check if the line is empty
    private int findEmpty(){
        for(int i = 0; i < SIZE; i++){
            if(Arrays.equals(LISTA_DE_REPRODUÇÃO[i], new String[INFO_SIZE])){
                return i;
            }
        }
        return 999;
    }
}
