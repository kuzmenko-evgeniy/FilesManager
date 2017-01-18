import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class FileManager {

    private Scanner in;
    private String pathFrom;
    private String pathTo;
    private int threadCount;
    private int bufferSize;
    private long copyFilesLength;

    public FileManager() {
        this.in = new Scanner(System.in);
    }

    public static void main(String[] args) {

        FileManager fm = new FileManager();

        System.out.println("Specify copy from:");
        fm.setPathFrom(fm.getUserDataString());
        fm.checkSourcePath(fm.getPathFrom());

        System.out.println("Specify copy to:");
        fm.setPathTo(fm.getUserDataString());

        System.out.println("Specify threads count (minimum 2):");
        fm.setThreadCount(Integer.parseInt(fm.getUserDataString()));

        if (fm.getThreadCount() < 2) {
            fm.setThreadCount(2);
            System.out.println("Threads count is set 2");
        }

        System.out.println("Specify buffer size (Kb):");
        fm.setBufferSize(Integer.parseInt(fm.getUserDataString()));

        ArrayList<String> dirs = new ArrayList<>();
        ArrayList<String> files = new ArrayList<>();

        ArrayList<String[]> filesInfo = new ArrayList<>();

        fm.checkSourcePath(fm.getPathFrom());

        Operation op = fm.getOperationType();

        //      fm.copyFilesLength = fm.scanDirs(fm.getPathFrom(), dirs, files);

        fm.scanDirs(fm.getPathFrom(), filesInfo);

        String sourceDir = "";

        if (op == Operation.DirToDest) {
            sourceDir = new File(fm.getPathFrom()).getName();
        }

        for (String[] file : filesInfo) {

            if (file[2].equals("1")) {
                File directory = new File(fm.getPathTo() + "\\" + sourceDir + "\\" + file[0].replace(fm.getPathFrom(), ""));
                if (!directory.exists()) {
                    directory.mkdirs();
                }
            }
        }

//        for (String dir : dirs) {
////                File directory = new File(dir.replace(fm.getPathFrom(), fm.getPathTo()));
////                if (!directory.exists()) {
////                    directory.mkdirs();
////                }
////            }
//            if (op == Operation.DirToDest) {
//                File directory = new File(fm.getPathTo() + "\\" + sourceDir + "\\" + dir.replace(fm.getPathFrom(), ""));
//                if (!directory.exists()) {
//                    directory.mkdirs();
//                }
//            }
//        }


        Thread countDown = new Thread(fm.new CountDown());

        countDown.start();

        boolean success = true;

        for (String[] file : filesInfo) {

            if(file[2].equals("1")){
                continue;
            }
//
//            String dest = file.replace(fm.getPathFrom(), fm.getPathTo());
//            String source = file;
//
//            if (!isDirectory) {
//                dest = fm.getPathTo() + dest;
//                source = fm.getPathFrom();
//            }

            String source = "";
            String dest = "";

            if (op == Operation.FileToFile) {
                source = fm.getPathFrom();
                dest = fm.getPathTo();
            } else if (op == Operation.DirToDest) {
                dest = fm.getPathTo() + "\\" + sourceDir + "\\ " + new File(fm.getPathFrom()).getName() + "\\" + file[1];
                source = file[0];
            }

            try (RandomAccessFile in = new RandomAccessFile(source, "r");
                 RandomAccessFile out = new RandomAccessFile(dest, "rw");) {

                ExecutorService executor = Executors.newFixedThreadPool(fm.getThreadCount());

                for (int i = 0; i < fm.getThreadCount(); i++) {
                    executor.execute(new Copier(in, out, fm.getBufferSize()));
                }

                executor.shutdown();

                while (!executor.isTerminated()) {/*NOP*/}

            } catch (IOException ex) {
                success = false;
                countDown.interrupt();
                System.out.println("An error occurred while copying files. Operation terminated");
                break;
            }
        }

        if (success) {
            countDown.interrupt();
            System.out.println("File(s) has been successfully copied.");
        }

    }

    private Operation getOperationType() {
        if (new File(pathFrom).isDirectory() & new File(getPathTo()).isDirectory()) {
            return Operation.DirToDest;
        } else if ((!(new File(pathFrom).isDirectory()) & !(new File(getPathTo()).isDirectory()))) {
            return Operation.FileToFile;
        } else {
            return Operation.FileToDir;
        }
    }

    public int getThreadCount() {
        return threadCount;
    }

    public void setThreadCount(int threadCount) {
        this.threadCount = threadCount;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize * 1024;
    }

    public String getPathFrom() {
        return pathFrom;
    }

    public void setPathFrom(String pathFrom) {
        this.pathFrom = pathFrom;
    }

    public String getPathTo() {
        return pathTo;
    }

    public void setPathTo(String pathTo) {
        this.pathTo = pathTo;
    }

    private void checkSourcePath(String path) {

        while (true) {

            File file = new File(path);

            if (!file.exists()) {
                System.out.println("Specified path does not exist");
                System.out.println("If you want other path write (y) or write (n) to exit");

                if (getUserDataString().equals("y")) {
                    System.out.println("Specify copy from:");
                    path = getUserDataString();

                    continue;
                } else {
                    System.exit(0);
                }
            }
            setPathFrom(path);
            break;
        }
    }

    private String getUserDataString() {
        return in.nextLine();
    }

    private void scanDirs(String path, ArrayList<String[]> fileInfo) {

        /*
        0 - source
        1 - name
        2 - is directory
         */

        File f = new File(path);
        File[] listFiles = f.listFiles();
        String info[] = new String[4];

        for (File file : listFiles) {

            if (!file.isDirectory()) {
                info[0] = file.getAbsolutePath();
                info[1] = file.getName();
                info[2] = "0";
                fileInfo.add(info);
                copyFilesLength = copyFilesLength + file.length();
            }else{
                info[0] = file.getAbsolutePath();
                info[1] = file.getName();
                info[2] = "1";
                fileInfo.add(info);
                copyFilesLength = copyFilesLength + file.length();
                scanDirs(file.getPath(), fileInfo);
            }

        }
    }

    class CountDown implements Runnable {
        @Override
        public void run() {
            try {
                long copied = 0;
                while (copyFilesLength != Copier.writtenBytes) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedException();
                    }
                    TimeUnit.SECONDS.sleep(1);

                    double changeSpeed = Copier.writtenBytes - copied;

                    int remainingTime = (int) ((copyFilesLength - Copier.writtenBytes) / changeSpeed);

                    copied = Copier.writtenBytes;

                    System.out.printf("remaining time ......... %4ds %10.2f MB/s%n", remainingTime, changeSpeed / 1024 / 1024);
                }
            } catch (InterruptedException e) {/*NOP*/}
        }
    }
}