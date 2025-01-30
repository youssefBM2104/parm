import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {

    public static String removeLeadingTab(String input) {
        if (input != null && input.startsWith("\t")) {
            return input.substring(1); // Remove the first character (tab)
        }
        return input; // Return the original string if no tab is found at the beginning
    }

    public static void fillList(String filename, List<List<String>> wordLists, Map<String, Integer> etiquettes) {

        int instructionCounter = 1;
        try {
            List<String> lines = Files.readAllLines(Path.of(filename));

            Pattern pattern = Pattern.compile("\\[[^\\]]+\\]|\\S+");

            for (String line : lines) {
                line = removeLeadingTab(line);
                // Skip lines starting with '@'
                if ( line.equals("run:") ||line.startsWith("@")|| line.startsWith(".") && !line.endsWith(":")) {
                    continue;
                }

                line = line.replace(",", "");

                List<String> words = new ArrayList<>();
                Matcher matcher = pattern.matcher(line);

                while (matcher.find()) {
                    String word = matcher.group();
                    if (word.startsWith(".") && word.endsWith(":")) {
                        word = word.substring(0, word.length() - 1);
                        etiquettes.put(word, instructionCounter);
                        continue;
                    }
                    words.add(word);

                }

                if (words.isEmpty()||!words.getFirst().equals("push")&&!words.getFirst().equals("pop")){
                    wordLists.add(words);
                }
                if (!words.isEmpty()) {
                    instructionCounter++;
                }
            }

        } catch (IOException e) {
            System.err.println("Error reading the file: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Please provide the filename as a command-line argument.");
            return;
        }

        final String filename = System.getProperty("user.dir")  +"/"+ args[0];
        Map<String, Integer> etiquettes = new HashMap<>();
        List<List<String>> wordLists = new ArrayList<>();

        fillList(filename, wordLists, etiquettes);


        int counter = 1;


        String result = "v2.0 raw\n";
        for (List<String> wordList : wordLists) {
            if (wordList.isEmpty()) {
                continue;
            }

            String currentBinary = "";
            Instruction instruction = Instruction.valueOf(wordList.getFirst().toUpperCase());
            currentBinary += instruction.buildBinary();

            wordList.removeFirst();
            if (instruction.equals(Instruction.LDR) || instruction.equals(Instruction.STR)) {
                currentBinary+= toBinaryString(extractNumber(wordList.getFirst()), 3);
                if (wordList.getLast().length()==4){
                    currentBinary+="00000000";
                }
                else {
                    currentBinary+= toBinaryString(extractNumberFromSpString(wordList.getLast())/4, 8);
                }
            } else if (currentBinary.isEmpty()) {
                switch (instruction) {
                    case CMP -> currentBinary += treatCMP(wordList);
                    case ADDS, SUBS -> currentBinary += treatADDSAndSUBBS(wordList, instruction);
                    case ASRS -> currentBinary += treatASRS(wordList);
                    case LSLS, LSRS -> currentBinary += treatLogicalShifts(wordList, instruction);
                }
            }


            else if (instruction.ordinal() >= Instruction.BEQ.ordinal() && instruction.ordinal() <= Instruction.B.ordinal()) {
                int instructionNum = etiquettes.get(wordList.getLast());

                int finalInstructionNum = instructionNum - counter - 3;

                if (instruction.equals(Instruction.B)) {
                    currentBinary += toBinaryString(finalInstructionNum, 11);
                } else {
                    currentBinary += toBinaryString(finalInstructionNum, 8);
                }
            } else {
                if (instruction.equals(Instruction.SUB) || instruction.equals(Instruction.ADD)) {
                    //wordList.removeFirst();
                    String regNumBinary = toBinaryString(extractNumber(wordList.getLast())/4, 7);
                    currentBinary += regNumBinary;
                }
                else if (instruction.isDataProc()) {
                    currentBinary += processDataProc(wordList);
                } else {
                    String regNumBinary = toBinaryString(extractNumber(wordList.getFirst()), 3);
                    currentBinary += regNumBinary;
                    if (wordList.getLast().contains("#")) {
                        regNumBinary = toBinaryString(extractNumber(wordList.getLast()), 8);
                    } else {
                        regNumBinary = toBinaryString(extractNumber(wordList.getLast()), 3);
                    }
                    currentBinary += regNumBinary;
                }
            }


            String hexa = binaryToHexa(currentBinary);
            result += hexa;
            result += " ";
            counter++;
        }

        writeFile(result,args[0]);

    }

    public static void writeFile(String content, String fileName){

        String binFile = getFileNameWithoutExtension(fileName)+".bin";

        try (FileWriter writer = new FileWriter(System.getProperty("user.dir")+"/MyBin/"+binFile)) {
            writer.write(content);
            System.out.println("File written successfully.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static String getFileNameWithoutExtension(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return filePath; // Return the input if it's null or empty
        }

        // Use Paths to extract the filename (with extension)
        Path path = Paths.get(filePath);
        String fileName = path.getFileName().toString(); // Gets the filename (e.g., "example.txt")

        // Remove the extension
        int lastDotIndex = fileName.lastIndexOf('.'); // Find the last occurrence of '.'

        return fileName.substring(0, lastDotIndex); // Return the substring before the last '.'
    }
    public static String processDataProc(List<String> wordList) {
        String regNumBinary = toBinaryString(extractNumber(wordList.getFirst()), 3);
        return toBinaryString(extractNumber(wordList.get(1)), 3) + regNumBinary;
    }

    public static String binaryToHexa(String binary) {
        int decimalValue = Integer.parseInt(binary, 2);

        return String.format("%04x", decimalValue);
    }

    public static String treatCMP(List<String> wordList) {
        String binaryStart = "0100001010";
        if (wordList.getLast().contains("#")) {
            binaryStart = "00101";
            String regNumBinary = toBinaryString(extractNumber(wordList.getFirst()), 3);
            return binaryStart + regNumBinary + toBinaryString(extractNumber(wordList.getLast()), 8);
        } else {
            return binaryStart + processDataProc(wordList);
        }
    }

    public static String treatASRS(List<String> wordList) {
        String binaryStart = "0100000100";
        if (wordList.getLast().contains("#")) {
            binaryStart = "00010";
            String regNumBinaries = "";
            for (String reg : wordList) {
                if (reg.contains("#")) {
                    regNumBinaries = toBinaryString(extractNumber(reg), 5) + regNumBinaries;
                } else {
                    regNumBinaries = toBinaryString(extractNumber(reg), 3) + regNumBinaries;
                }
            }
            return binaryStart + regNumBinaries;
        } else {
            return binaryStart + processDataProc(wordList);
        }
    }

    public static String treatADDSAndSUBBS(List<String> wordList, Instruction instruction) {
        String binaryStart;
        switch (wordList.size()) {
            case 3:
                if (wordList.getLast().contains("#")) {
                    binaryStart = instruction.equals(Instruction.ADDS) ? "0001110" : "0001111";
                } else {
                    binaryStart = instruction.equals(Instruction.ADDS) ? "0001100" : "0001101";
                }
                String regNumBinaries = "";

                for (String reg : wordList) {
                    regNumBinaries = toBinaryString(extractNumber(reg), 3) + regNumBinaries;
                }
                return binaryStart + regNumBinaries;
            case 2:
                binaryStart = instruction.equals(Instruction.ADDS) ? "00110" : "00111";
                String regNumBinary = toBinaryString(extractNumber(wordList.getFirst()), 3);
                return binaryStart + regNumBinary + toBinaryString(extractNumber(wordList.getLast()), 8);
        }
        return null;
    }

    public static String treatLogicalShifts(List<String> wordList, Instruction instruction) {
        String binaryStart;
        switch (wordList.size()) {
            case 3:
                binaryStart = instruction.equals(Instruction.LSLS) ? "00000" : "00001";
                String regNumBinaries = "";

                for (String reg : wordList) {
                    if (reg.contains("#")) {
                        regNumBinaries = toBinaryString(extractNumber(reg), 5) + regNumBinaries;
                    } else {
                        regNumBinaries = toBinaryString(extractNumber(reg), 3) + regNumBinaries;
                    }
                }
                return binaryStart + regNumBinaries;
            case 2:
                binaryStart = instruction.equals(Instruction.LSLS) ? "0100000010" : "0100000011";
                return binaryStart + processDataProc(wordList);
        }
        return null;
    }

    public static String toBinaryString(int number, int bits) {
        String binary = Integer.toBinaryString(number);

        while (binary.length() < bits) {
            binary = "0" + binary;
        }

        if (binary.length() > bits) {
            binary = binary.substring(binary.length() - bits);
        }

        return binary;
    }

    public static int extractNumber(String input) {


        return Integer.parseInt(input.replaceAll("^[a-zA-Z#]", ""));


    }
    public static int extractNumberFromSpString(String input) {

        int hashIndex = input.indexOf("#");
        int endIndex = input.indexOf("]", hashIndex);

        String numberPart = input.substring(hashIndex + 1, endIndex);
        return Integer.parseInt(numberPart);
    }

    public enum Instruction {
        ASRS("asrs"),
        MOVS("movs"),
        ANDS("ands"),
        EORS("eors"),
        ADCS("adcs"),
        SBCS("sbsc"),
        RORS("rors"),
        TST("tst"),
        RSBS("rsbs"),
        CMN("cmn"),
        ORRS("orrs"),
        MULS("muls"),
        BICS("bics"),
        MVNS("mvns"),
        STR("str"),
        LDR("ldr"),
        ADD("add"),
        SUB("sub"),
        BEQ("bEQ"),
        BNE("bNE"),
        BCS("bCS"),
        BHS("bHS"),
        BCC("bCC"),
        BLO("bLO"),
        BMI("bMI"),
        BPL("bPL"),
        BVS("bVS"),
        BVC("bVC"),
        BHI("bHI"),
        BLS("bLS"),
        BGE("bGE"),
        BLT("bLT"),
        BGT("bGt"),
        BLE("bLE"),
        BAL("bAL"),
        B("b"),
        CMP("cmp"),
        ADDS("adds"),
        SUBS("subs"),
        LSLS("lsls"),
        LSRS("lsrs")
        ;

        String instructionString;
        Instruction(String instruction){
            this.instructionString = instruction;
        }

        public boolean isDataProc(){
            return this.ordinal()>= ANDS.ordinal() && this.ordinal() <= MVNS.ordinal();
        }

        public String buildBinary(){
            return switch (this){
                case MOVS -> "00100";
                case ANDS -> "0100000000";
                case EORS -> "0100000001";
                case ADCS -> "0100000101";
                case SBCS -> "0100000110";
                case RORS -> "0100000111";
                case TST -> "0100001000";
                case RSBS ->"0100001001" ;
                case CMN -> "0100001011";
                case ORRS -> "0100001100";
                case MULS -> "0100001101";
                case BICS -> "0100001110";
                case MVNS -> "0100001111";
                case STR -> "10010";
                case LDR -> "10011";
                case ADD -> "101100000";
                case SUB -> "101100001";
                case BEQ -> "11010000";
                case BNE -> "11010001";
                case BCS -> "11010010";
                case BHS -> "11010010";
                case BCC -> "11010011";
                case BLO -> "11010011";
                case BMI -> "11010100";
                case BPL -> "11010101";
                case BVS -> "11010110";
                case BVC -> "11010111";
                case BHI -> "11011000";
                case BLS -> "11011001";
                case BGE -> "11011010";
                case BLT -> "11011011";
                case BGT -> "11011100" ;
                case BLE -> "11011101";
                case BAL -> "11011110";
                case B -> "11100";
                default -> "";
            };
        }
    }
}
