import com.mpatric.mp3agic.InvalidDataException;
import com.mpatric.mp3agic.UnsupportedTagException;
import javazoom.jl.decoder.*;
import javazoom.jl.player.AudioDevice;
import javazoom.jl.player.FactoryRegistry;
import support.PlayerWindow;
import support.Song;

import javax.swing.event.MouseInputAdapter;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Player {

    /**
     * Constants
     * */

    //Max Playlist Size
    private static final int SIZE = 1000;

    // Info = "Title", "Album", "Artist", "Year", "Length", "Path"
    private static final int INFO_SIZE = 6;

    // Program name
    private static final String TITULO_DA_JANELA = "Mp3";

    /**
     * We use two types of list, one to store all the music information (playlist)
     * and the other to store only the headers (LISTA_DE_REPRODUÇÃO)
     * */

    // All the music information
    private Song[] playlist = new Song[SIZE];

    // Only the headers
    private String[][] LISTA_DE_REPRODUÇÃO = new String[SIZE][INFO_SIZE];

    /**
     * Global Thread variables
     * */

    public Lock lock = new ReentrantLock();
    public Condition action = lock.newCondition();
    public boolean using = false;
    public boolean pause = false;

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


    /**
     * Activates the functions by clicking the corresponding button
     * */

    private final ActionListener buttonListenerPlayNow = e ->new Thread(new playNowThread()).start();
    private final ActionListener buttonListenerRemove = e -> new Thread(new removeThread()).start() ;
    private final ActionListener buttonListenerAddSong = e -> new Thread(new addSongThread()).start() ;
    private final ActionListener buttonListenerPlayPause = e ->new Thread(new playPauseThread()).start() ;
    private final ActionListener buttonListenerStop = e ->System.out.println(e) ;
    private final ActionListener buttonListenerNext = e ->System.out.println(e) ;
    private final ActionListener buttonListenerPrevious = e -> System.out.println(e);
    private final ActionListener buttonListenerShuffle = e -> System.out.println(e);
    private final ActionListener buttonListenerLoop = e ->System.out.println(e) ;

    /**
     * Check Mouse Events
     * */
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

    /**
     * Starts Player
     * */

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

    /**
     * Thread Functions
     **/

    // Add music to playlist
    class addSongThread implements Runnable{
        public void run(){

            lock.lock();
            while (using) {
                try {
                    action.wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            using = true;
            addSong();
            using = false;
            action.signalAll();
            lock.unlock();

        }
    }
    //Remove music from playlist
    class removeThread implements Runnable{
        public void run(){

            lock.lock();
            while (using) {
                try {
                    action.wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            using = true;
            remove();
            using = false;
            action.signalAll();
            lock.unlock();

        }
    }
    // Play the selected song
    class playNowThread implements Runnable {
        public void run() {
            try {
                lock.lock();
                pause = false;
                String id = window.getSelectedSong();
                Song song = getSong(id);
                playNow();
                device = FactoryRegistry.systemRegistry().createAudioDevice();
                device.open(decoder = new Decoder());
                bitstream = new Bitstream(song.getBufferedInputStream());
                currentFrame = 0;
                lock.unlock();
                while (true ){

                    if(!pause){

                        lock.lock();
                        while (using) {
                            try {
                                action.wait();
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        }
                        using = true;

                        if(!playNextFrame()) break;
                        window.setTime((int) (currentFrame * song.getMsPerFrame()), (int) song.getMsLength());
                        currentFrame += 1;

                        using = false;
                        action.signalAll();
                        lock.unlock();
                        TimeUnit.MILLISECONDS.sleep(1);
                    }

                }


            } catch (JavaLayerException | FileNotFoundException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

    }
    // Play/Pause the current song
    class playPauseThread implements Runnable{
        public void run(){

            lock.lock();
            while (using) {
                try {
                    action.wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            using = true;
            playPause();
            using = false;
            action.signalAll();
            lock.unlock();
        }
    }


    /**
     * Functions inside the Threads
     * */

    // Add music to playlist
    public void addSong(){
        try{
            Song song = window.openFileChooser();
            if(song != null){
                playlist[findEmpty()] = song;
                System.arraycopy(song.getDisplayInfo(), 0,LISTA_DE_REPRODUÇÃO[findEmpty()] , 0, INFO_SIZE);
                window.setQueueList(LISTA_DE_REPRODUÇÃO);
            }

        }catch (InvalidDataException | BitstreamException | UnsupportedTagException | IOException exception){
            System.out.println(exception.getMessage());
        }
    }
    // Remove music from playlist
    public void remove(){
        String id = window.getSelectedSong();
        removeSong(id);
        window.setQueueList(LISTA_DE_REPRODUÇÃO);
    }
    // Play the selected song
    public void playNow() {


        String id = window.getSelectedSong();

        Song song = getSong(id);

        if (song != null) {

            window.setPlayingSongInfo(song.getTitle(),song.getAlbum(),song.getArtist());
            window.setEnabledLoopButton(false);
            window.setEnabledScrubber(true);
            window.setEnabledNextButton(false);
            window.setEnabledPlayPauseButton(true);
            window.setPlayPauseButtonIcon(0);
            window.setEnabledStopButton(true);
            window.setEnabledPreviousButton(true);
            window.setEnabledShuffleButton(true);
        }
    }
    // Play/Pause the current song
    public void playPause(){
        if (pause) {
            window.setPlayPauseButtonIcon(0);
            pause = false;
        }
        else{
            window.setPlayPauseButtonIcon(1);
            pause = true;
        }
    }

    /**
     * Auxiliar Functions
     * */

    // Find the next empty space in the playlist
    private int findEmpty(){
        for(int i = 0; i < SIZE; i++){
            if(Arrays.equals(LISTA_DE_REPRODUÇÃO[i], new String[INFO_SIZE])){
                return i;
            }
        }
        return 999;
    }
    // Remove the song
    private void removeSong(String id){
        for(int i = 0; i < SIZE; i++){
            if(Objects.equals(LISTA_DE_REPRODUÇÃO[i][5], id)){
                playlist[i] = null;
                LISTA_DE_REPRODUÇÃO[i] = new String[INFO_SIZE];
                return;
            }
        }
    }
    // Find the song in the playlist
    private Song getSong(String id){
        for(int i = 0; i < SIZE; i++){
            if(Objects.equals(LISTA_DE_REPRODUÇÃO[i][5], id)){
                return playlist[i];
            }
        }
        return null;
    }

}
