import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.util.*;

public class Client {
    static int port = 70;
    static String host = "comp3310.ddns.net";
    static String queryResultPath = "./queryResults";
    static String DownloadPath = "./Downloads";
    private static java.util.Date date = new java.util.Date();

    /**
     * This class represents an item Obejct.
     */
    public static class Item {
        private static int nextItemID=0;
        private int ItemID;
        private Type type;
        private String ItemSelector;
        private String hostOfItem;
        private String portOfHost;
        private String rawType;
        private int sizeInByte;

        enum Type {
            Text,
            Binary,
            Directory,
            Error
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (!(obj instanceof Item)) return false;

            Item item = (Item) obj;

            return item.type == this.type &&
                    item.ItemSelector.equals(this.ItemSelector) &&
                    item.hostOfItem.equals(this.hostOfItem) &&
                    item.portOfHost.equals(this.portOfHost) &&
                    item.rawType.equals(this.rawType);
        }

        @Override
        public int hashCode() {
            return this.type.hashCode()+
                    this.hostOfItem.hashCode()+
                    this.ItemSelector.hashCode()+
                    this.portOfHost.hashCode()+
                    this.rawType.hashCode();
        }

        public Item(Type type, String ItemSelector, String hostOfItem, String portOfHost, String rawType, int sizeInByte) {
            this.ItemID = nextItemID++;
            this.type = type;
            this.ItemSelector = ItemSelector;
            this.hostOfItem = hostOfItem;
            this.portOfHost = portOfHost;
            this.rawType = rawType;
            this.sizeInByte = sizeInByte;
        }

        @Override
        public String toString() {
            return this.type + " (raw Type "+this.rawType+" File size: "+this.sizeInByte+" bytes) "+ ItemID + " with selector: "+ this.ItemSelector + " on host: " + this.hostOfItem + " at port: " + this.portOfHost;
        }

        /**
         * Detect if the Item b with Directory type has loop in the directory (link to itself) e.g. b/b/b/b/b
         * @param b Item to detect loop
         * @return false if the b is not A Directory Item or has no loops. true otherwise.
         */
        public static boolean loop(Item b) {
            if (b.type != Type.Directory) {
                return false;
            }

            String[] dirAsArray = b.ItemSelector.split("/");
            int length = dirAsArray.length;
            if (length >= 2) {
                for (int i = 0; i < length - 1; i++) {
                    if (dirAsArray[i].equals(dirAsArray[length - 1]))
                        return true;
                }
            }
            return false;
        }

        /**
         * Create an Item obejct based on the RFC1436 protocol,
         * however, the Item will be either Text, or non-Text(binary and directory).
         *
         * @param charType The char indicating the item type.
         * @param ItemSelector The selector of the item on the server.
         * @param hostOfItem The host of the item located at.
         * @param portOfHost The port of the host
         * @return An Item object created based on the type
         */
        static Item createItem(char charType, String ItemSelector, String hostOfItem, String portOfHost) {

            if(Character.isDigit(charType)) {
                int t = Character.getNumericValue(charType);
                if (t == 0) return new Item(Type.Text, ItemSelector, hostOfItem, portOfHost, "0",0);
                else if (t == 1) {
                    return new Item(Type.Directory, ItemSelector, hostOfItem, portOfHost, "1",0);
                } else if (t == 3) {
                    return new Item(Type.Error, ItemSelector, hostOfItem, portOfHost, "3",0);
                    //throw new IllegalArgumentException("Item type is 3, which is an error from the gopher server");
                }
            }

            return new Item(Type.Binary, ItemSelector, hostOfItem, portOfHost, String.valueOf(charType),0);
        }

        /**
         * Create a list of items based on a given query result contains the items.
         * @return A list of all the items in that query result.
         * @throws IOException
         */
        public static Items createItems(String pathToQuery) throws IOException {
            BufferedReader bf = new BufferedReader(new FileReader(pathToQuery));
            ArrayList<Item> textItems = new ArrayList<>();
            ArrayList<Item> binaryItems = new ArrayList<>();
            ArrayList<Item> directoryItems = new ArrayList<>();
            ArrayList<Item> errorItems = new ArrayList<>();
            String line;

            while ((line=bf.readLine()) != null) {
                String[] array = line.split("\\t");
                if (array[0].charAt(0) != 'i') {
                    Item temp = createItem(array[0].charAt(0),array[1],array[2],array[3]);

                    if (temp.type == Type.Text) textItems.add(temp);
                    else if (temp.type == Type.Binary) {
                        binaryItems.add(temp);
                    } else if (temp.type == Type.Directory) {
                        directoryItems.add(temp);
                    } else {
                        errorItems.add(temp);
                    }
                    //items.add(createItem(array[0].charAt(0),array[1],array[2],array[3]));
                }
            }
            if (textItems.size()!=0 || binaryItems.size()!=0 || directoryItems.size()!=0 || errorItems.size()!=0) {
                return new Items(textItems,binaryItems,directoryItems,errorItems);
            }

            return null;
        }
    }

    public static class Items {
        List<Item> textItems;
        List<Item> binItems;
        List<Item> dirItems;
        List<Item> errItems;

        public Items(List<Item> textItems,
                     List<Item> binItems,
                     List<Item> dirItems,
                     List<Item> errItems) {
            this.textItems = textItems;
            this.binItems = binItems;
            this.dirItems = dirItems;
            this.errItems = errItems;
        }

        public Items() {
            this.textItems = new ArrayList<>();
            this.binItems = new ArrayList<>();
            this.dirItems = new ArrayList<>();
            this.errItems = new ArrayList<>();
        }

        public void mergeItems(List<Items> itemsList) {
            if (itemsList != null) {
                for (Items items:itemsList) {
                    this.textItems.addAll(items.textItems);
                    this.binItems.addAll(items.binItems);
                    this.dirItems.addAll(items.dirItems);
                    this.errItems.addAll(items.errItems);
                }
            }
        }

    }

    /**
     * This method will send query to the gopher server in this assignment,
     * and store the query result under the directory ./queryResults with the given file name.
     * It also offers an option to remove the last line of the result file.
     *
     * @param query The query to send to the gopher server.
     * @param resultFileName The result file name (file extension required).
     * @param removeLastLine Whether to remove the last line when storing the file.
     * @throws IOException
     */
    static void queryServer(String query, String resultFileName, boolean removeLastLine) throws IOException {

        Socket sock = new Socket(host, port);


        BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(sock.getOutputStream()));

        System.out.println(new Timestamp(date.getTime()) + " Client sending request: "+query+"<CR><LF>");

        bufferedWriter.write(query+"\r\n");
        bufferedWriter.flush();

        String inMsg;
        File queryResult = null;
        File f = null;


        f = new File(queryResultPath);
        if (f.exists() && f.isDirectory()) {

        } else {
            Files.createDirectory(Paths.get(f.getPath()));
        }


        queryResult = new File(queryResultPath+"/"+resultFileName);


        if (!queryResult.exists()) {
            queryResult.createNewFile();
        }

        List<String> inMsgAsList = new ArrayList<>();
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(sock.getInputStream()));

        while ((inMsg = bufferedReader.readLine()) != null) {
            //bufferedWriterToFile.write(inMsg);
            inMsgAsList.add(inMsg);
            //bufferedWriterToFile.newLine();
        }

        bufferedWriter.close();
        bufferedReader.close();

        int endIndex = removeLastLine ? inMsgAsList.size()-1 : inMsgAsList.size();

        BufferedWriter bufferedWriterToFile = new BufferedWriter(new FileWriter(queryResult));

        for (int j = 0; j < endIndex; j++) {
            bufferedWriterToFile.write(inMsgAsList.get(j));
            bufferedWriterToFile.flush();
            if (j != endIndex-1)
                bufferedWriterToFile.newLine();
        }
        bufferedWriterToFile.close();

        sock.close();
    }

    static void downloadFile(Item item, boolean printRequest) throws IOException {

        Socket sock = new Socket(host, port);

        DataOutputStream out = new DataOutputStream(sock.getOutputStream());

        if (printRequest)
            System.out.println(new Timestamp(date.getTime()) + " Client sending request: "+item.ItemSelector+"<CR><LF>");


        out.write((item.ItemSelector+"\r\n").getBytes(StandardCharsets.UTF_8));
        out.flush();

        String resultFileName = item.type+""+item.ItemID+(item.type==Item.Type.Binary ? ".bin" : ".txt");

        File f = new File(DownloadPath);

        if (f.exists() && f.isDirectory()) {

        } else {
            Files.createDirectory(Paths.get(f.getPath()));
        }

        File queryResult = new File(DownloadPath+"/"+resultFileName);


        if (!queryResult.exists()) {
            queryResult.createNewFile();
        }

        DataInputStream in = new DataInputStream(sock.getInputStream());

        DataOutputStream outToFile = new DataOutputStream(new FileOutputStream(queryResult));

        byte[] previousbuffer = new byte[500];
        int previousNBytes;
        byte[] buffer = new byte[500];
        int nBytes;
        int total_size = 0;
        if (!item.rawType.equals("5") && !item.rawType.equals("9")) {
            while ((nBytes = in.read(buffer)) != -1) {
                previousbuffer = buffer.clone();
                previousNBytes = nBytes;
                if ((nBytes = in.read(buffer)) != -1) {
                    outToFile.write(previousbuffer, 0, previousNBytes);
                    outToFile.write(buffer,0,nBytes);
                    out.flush();
                    total_size+=nBytes;
                    total_size+=previousNBytes;
                } else {
                    outToFile.write(previousbuffer, 0, previousNBytes-3);
                    out.flush();
                    total_size+=previousNBytes-3;
                }
            }
        } else {
            while ((nBytes = in.read(buffer)) != -1) {
                outToFile.write(buffer, 0, nBytes);
                outToFile.flush();
                total_size+=nBytes;
            }
        }
        item.sizeInByte = total_size;

        outToFile.close();
        out.close();
        in.close();
        sock.close();
    }

    static boolean isExternal(Item item) {
        return !item.hostOfItem.equals(host);
    }

    static Items crawlServer() throws IOException {
        // first get the list of all things from the gopher sever
        queryServer("", "MainPage.txt",true);
        Items items = Item.createItems(queryResultPath+"/"+"MainPage.txt");

        List<Items> itemsList = new ArrayList<>();
        // check if there is any directory to crawl with
        if (items != null && items.dirItems.size() != 0) {
            List<Item> visitedDir = new ArrayList<>();
            for (Item dir: items.dirItems) {
                Items temp = crawl(dir, visitedDir);
                if (temp != null)
                    itemsList.add(temp);
            }
        }

        if (items != null)
            items.mergeItems(itemsList);
        return items;
    }

    static Items crawl(Item dir, List<Item> visitedDir) throws IOException {
        // avoid loops
        if (Item.loop(dir)) return null;

        //System.out.println("Exploring Dir: "+dir.ItemSelector);
        List<Items> itemsList = new ArrayList<>();
        Items resultItems = null;
        if (visitedDir.stream().noneMatch(d -> d.equals(dir))) {
            //add the dir to be visit to the visited list
            visitedDir.add(dir);
            // only explore dir on the host server.
            if (!isExternal(dir)) {
                //Map<Item.Type, List<Item>> items = new HashMap<>();

                String resultFileName = dir.type+"" +dir.ItemID+".txt";
                // explore dir
                queryServer(dir.ItemSelector,resultFileName,true);

                // create items based on the query result.
                resultItems = Item.createItems(queryResultPath+"/"+resultFileName);

                // check if the dir is empty
                if (resultItems != null && resultItems.dirItems.size()!=0) {

                    // keep crawling each new dir and collect the items.
                    for (Item nextDir : resultItems.dirItems) {
                        //Map<Item.Type, List<Item>> temp = new HashMap<>();
                        Items temp = crawl(nextDir,visitedDir);
                        if (temp!= null) {
                            itemsList.add(temp);
                        }
                    }
                }
            } else {

            }
        }
        if (resultItems != null) {
            resultItems.mergeItems(itemsList);
        }

        return resultItems;
    }



    public static void main (String[] args) {
        try {

            Items items = crawlServer();

            Set<Item> uniqueDir = new HashSet<>(items.dirItems);
            Set<Item> uniqueTxt = new HashSet<>(items.textItems);
            Set<Item> uniqueBin = new HashSet<>(items.binItems);

            List<Item> external = new LinkedList<>();

            for (Item item : items.dirItems) {
                if (isExternal(item)) external.add(item);
            }

            for (Item item : items.textItems) {
                if (isExternal(item)) external.add(item);
            }

            for (Item item : items.binItems) {
                if (isExternal(item)) external.add(item);
            }
            System.out.println();
            System.out.println("The number of unique text files: "+uniqueTxt.size());
            System.out.println("The number of all text files: "+items.textItems.size());
            System.out.println("The list of text files: ");
            for (Item item : uniqueTxt) {
                downloadFile(item,false);
                System.out.println(item);
                System.out.println();
            }

            System.out.println();
            System.out.println("The number of unique binary files: "+uniqueBin.size());
            System.out.println("The number of all binary files: "+items.binItems.size());
            System.out.println("The list of binaries: ");
            for (Item item : uniqueBin) {
                downloadFile(item,false);
                System.out.println(item);
                System.out.println();
            }

            System.out.println();
            System.out.println("The number of all directories: "+items.dirItems.size());
            System.out.println("The number of unique directories: "+ uniqueDir.size());
            System.out.println("The list of unique directories: ");
            for (Item item: uniqueDir) {
                System.out.println(item);
            }


            System.out.println();
            System.out.println("The number of invalid reference: "+ items.errItems.size());
            System.out.println("The number of external reference: "+ external.size());

        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
