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

    public boolean playnow = true;
    public boolean pause = false;
    public boolean next = false;
    public boolean previous = false;

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

    Thread playNow = new Thread(new playNowThread());
    Thread remove = new Thread(new removeThread());
    Thread addSong = new Thread(new addSongThread());
    Thread playPause = new Thread(new playPauseThread());
    Thread stop = new Thread(new stopThread());
    Thread nextSong = new Thread(new nextThread());
    Thread previousSong = new Thread(new previousThread());
    /**
     * Activates the functions by clicking the corresponding button
     * */

    private final ActionListener buttonListenerPlayNow = e -> {
        try {
            playNow();
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    };
    private final ActionListener buttonListenerRemove = e ->  remove();
    private final ActionListener buttonListenerAddSong = e ->  addSong();
    private final ActionListener buttonListenerPlayPause = e -> playPause();
    private final ActionListener buttonListenerStop = e -> stop();
    private final ActionListener buttonListenerNext = e -> nextSong();
    private final ActionListener buttonListenerPrevious = e ->  previousSong();
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
     * Inicialize Thread Functions
     **/

    private void playNow() throws InterruptedException {
        if (playNow.isAlive()) {
            playNow.interrupt();
            TimeUnit.SECONDS.sleep(2);
        }
        playNow = new Thread(new playNowThread());
        playNow.start();
    }

    private void remove(){
        if (remove.isAlive()) {
            remove.interrupt();
        }
        remove.run();
    }
    private void addSong(){
        if (addSong.isAlive()) {
            addSong.interrupt();
        }
        addSong.run();
    }
    private void playPause(){
        if (playPause.isAlive()) {
            playPause.interrupt();
        }
        playPause.run();
    }
    private void stop(){
        if (stop.isAlive()) {
            stop.interrupt();
        }
        stop.run();
    }
    private void nextSong(){
        if (nextSong.isAlive()) {
            nextSong.interrupt();
        }
        nextSong.run();
    }
    private void previousSong(){
        if (previousSong.isAlive()) {
            previousSong.interrupt();
        }
        previousSong.run();
    }


    /**
     * Thread Functions
     **/

    // Add music to playlist
    class addSongThread implements Runnable{
        public void run(){
            int id = SIZE;

            lock.lock();

            while (using) {
                try {
                    action.wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            using = true;

            try{
                Song song = window.openFileChooser();
                if(song != null){
                    for(int i = 0; i < SIZE; i++){
                        if(Arrays.equals(LISTA_DE_REPRODUÇÃO[i], new String[INFO_SIZE])){
                            id = i;
                            break;
                        }
                    }
                    playlist[id] = song;
                    System.arraycopy(song.getDisplayInfo(), 0,LISTA_DE_REPRODUÇÃO[id] , 0, INFO_SIZE);
                    window.setQueueList(LISTA_DE_REPRODUÇÃO);
                }

            }catch (InvalidDataException | BitstreamException | UnsupportedTagException | IOException exception){
                System.out.println(exception.getMessage());
            }

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
            String id = window.getSelectedSong();

            //change the location of all songs below the removed one
            for(int i = 0; i < SIZE; i++){
                if(Objects.equals(LISTA_DE_REPRODUÇÃO[i][5], id)){
                    playlist[i] = null;
                    System.arraycopy(new String[INFO_SIZE], 0 , LISTA_DE_REPRODUÇÃO[i], 0, INFO_SIZE);
                    for(int j = i; j < SIZE - 1; j++){
                        System.arraycopy(LISTA_DE_REPRODUÇÃO[j + 1], 0 , LISTA_DE_REPRODUÇÃO[j], 0, INFO_SIZE);
                        playlist[j] = playlist[j + 1];
                    }
                }
            }
            window.setQueueList(LISTA_DE_REPRODUÇÃO);
            using = false;
            action.signalAll();
            lock.unlock();

        }
    }
    // Play the selected song
    class playNowThread implements Runnable {
        boolean loop = true;

        public void run() {
            try {
                //get the song
                lock.lock();
                while (using) {
                    try {
                        action.wait();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                using = true;
                pause = false;


                String id = window.getSelectedSong();
                Song song = getSong(id);
                if (song != null) {
                    window.setPlayingSongInfo(song.getTitle(),song.getAlbum(),song.getArtist());
                    window.setEnabledLoopButton(true);
                    window.setEnabledScrubber(true);
                    window.setEnabledNextButton(true);
                    window.setEnabledPlayPauseButton(true);
                    window.setPlayPauseButtonIcon(1);
                    window.setEnabledStopButton(true);
                    window.setEnabledPreviousButton(true);
                    window.setEnabledShuffleButton(true);
                }
                device = FactoryRegistry.systemRegistry().createAudioDevice();
                device.open(decoder = new Decoder());
                bitstream = new Bitstream(song.getBufferedInputStream());
                currentFrame = 0;
                using = false;
                action.signalAll();
                lock.unlock();

                //play the song
                while (loop ){
                        lock.lock();
                        while (using) {
                            try {
                                action.wait();
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        }
                        using = true;
                        String nextId = null;
                        String previousId = null;

                        //get next
                        for(int i = 0; i < SIZE - 1; i++){
                            if(Objects.equals(LISTA_DE_REPRODUÇÃO[i][5], id)){
                                if (i >= SIZE){
                                    nextId = null;
                                    break;
                                }
                                nextId =  LISTA_DE_REPRODUÇÃO[i + 1][5];
                                break;
                            }
                        }

                        //get previous
                        for(int i = 0; i < SIZE; i++){
                            if(Objects.equals(LISTA_DE_REPRODUÇÃO[i][5], id)){
                                if (i == 0){
                                    previousId = null;
                                    break;
                                }
                                previousId =  LISTA_DE_REPRODUÇÃO[i - 1][5];
                                break;
                            }
                        }

                    //change for the next song
                        if(nextId != null){
                            window.setEnabledNextButton(true);
                            if(next){
                                next = false;
                                id = nextId;
                                song = getSong(id);

                                if (song != null) {
                                    window.setPlayingSongInfo(song.getTitle(),song.getAlbum(),song.getArtist());
                                    window.setEnabledLoopButton(true);
                                    window.setEnabledScrubber(true);
                                    window.setEnabledNextButton(true);
                                    window.setEnabledPlayPauseButton(true);
                                    window.setPlayPauseButtonIcon(1);
                                    window.setEnabledStopButton(true);
                                    window.setEnabledPreviousButton(true);
                                    window.setEnabledShuffleButton(true);
                                }


                                device = FactoryRegistry.systemRegistry().createAudioDevice();
                                device.open(decoder = new Decoder());
                                bitstream = new Bitstream(song.getBufferedInputStream());
                                currentFrame = 0;
                                window.setTime((int) (currentFrame * song.getMsPerFrame()), (int) song.getMsLength());

                            }
                        }else
                        {
                            window.setEnabledNextButton(false);
                        }


                        //change for the previous song
                        if(previousId != null){
                            window.setEnabledPreviousButton(true);
                            if(previous) {
                                previous = false;
                                id = previousId;
                                song = getSong(id);


                                if (song != null) {
                                    window.setPlayingSongInfo(song.getTitle(),song.getAlbum(),song.getArtist());
                                    window.setEnabledLoopButton(true);
                                    window.setEnabledScrubber(true);
                                    window.setEnabledNextButton(true);
                                    window.setEnabledPlayPauseButton(true);
                                    window.setPlayPauseButtonIcon(1);
                                    window.setEnabledStopButton(true);
                                    window.setEnabledPreviousButton(true);
                                    window.setEnabledShuffleButton(true);
                                }


                                device = FactoryRegistry.systemRegistry().createAudioDevice();
                                device.open(decoder = new Decoder());
                                bitstream = new Bitstream(song.getBufferedInputStream());
                                currentFrame = 0;
                                window.setTime((int) (currentFrame * song.getMsPerFrame()), (int) song.getMsLength());
                            }
                        } else {
                            window.setEnabledPreviousButton(false);
                        }
                  //  System.out.println(window.getScrubberValue() + " " + currentFrame );
                    //play the next song when the current one ends

                    
                    if(!pause){
                        if(!playNextFrame()) {
                            next = false;
                            id = nextId;
                            if(nextId != null){
                                song = getSong(id);


                                if (song != null) {
                                    window.setPlayingSongInfo(song.getTitle(),song.getAlbum(),song.getArtist());
                                    window.setEnabledLoopButton(true);
                                    window.setEnabledScrubber(true);
                                    window.setEnabledNextButton(true);
                                    window.setEnabledPlayPauseButton(true);
                                    window.setPlayPauseButtonIcon(1);
                                    window.setEnabledStopButton(true);
                                    window.setEnabledPreviousButton(true);
                                    window.setEnabledShuffleButton(true);
                                }

                                device = FactoryRegistry.systemRegistry().createAudioDevice();
                                device.open(decoder = new Decoder());
                                bitstream = new Bitstream(song.getBufferedInputStream());
                                currentFrame = 0;
                            }
                        }

                        currentFrame += 1;
                    }
                    int time = (window.getScrubberValue());
                    int frame = (int)(time/song.getMsPerFrame());
                    if(currentFrame < frame + 1 ){
                        skipToFrame(frame);
                        currentFrame = frame;}
                    window.setTime((int) (currentFrame * song.getMsPerFrame()), (int) song.getMsLength());











                    using = false;
                    action.signalAll();
                    lock.unlock();
                    TimeUnit.MILLISECONDS.sleep(10);
                }


            } catch (JavaLayerException | FileNotFoundException | InterruptedException ignored) {

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
            if (pause) {
                window.setPlayPauseButtonIcon(1);
                pause = false;
            }
            else{
                window.setPlayPauseButtonIcon(0);
                pause = true;
            }
            using = false;
            action.signalAll();
            lock.unlock();
        }
    }
    // Stop the current song
    class stopThread implements Runnable{
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
            device = null;
            bitstream = null;
            window.resetMiniPlayer();
            currentFrame = 0;
            pause = true;
            using = false;
            action.signalAll();
            lock.unlock();
        }
    }
    // Change for the previous song
    class previousThread implements Runnable{
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
            previous = true;
            using = false;
            action.signalAll();
            lock.unlock();
        }
    }
    // Change for the next song
    class nextThread implements Runnable{
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
            next = true;
            using = false;
            action.signalAll();
            lock.unlock();
        }
    }


    /**
     * Auxiliar Functions
     * */

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
