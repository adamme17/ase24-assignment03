import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Fuzzer {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java Fuzzer.java \"<command_to_fuzz>\"");
            System.exit(1);
        }
        String commandToFuzz = args[0];
        String workingDirectory = "./";

        if (!Files.exists(Paths.get(workingDirectory, commandToFuzz))) {
            throw new RuntimeException("Could not find command '%s'.".formatted(commandToFuzz));
        }

        String seedInput = "<html a=\"value\">...</html>";

        ProcessBuilder builder = getProcessBuilderForCommand(commandToFuzz, workingDirectory);
        System.out.printf("Command: %s\n", builder.command());

        runCommand(builder, seedInput, getMuatedInputs(seedInput));
    }

    private static ProcessBuilder getProcessBuilderForCommand(String command, String workingDirectory) {
        ProcessBuilder builder = new ProcessBuilder();
        boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
        if (isWindows) {
            builder.command("cmd.exe", "/c", command);
        } else {
            builder.command("sh", "-c", command);
        }
        builder.directory(new File(workingDirectory));
        builder.redirectErrorStream(true);
        return builder;
    }

    private static void runCommand(ProcessBuilder builder, String seedInput, List<String> mutatedInputs) {
        Stream.concat(Stream.of(seedInput), mutatedInputs.stream()).forEach(input -> {
            try {
                Process process = builder.start();
                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()))) {
                    writer.write(input);
                    writer.flush();
                    writer.close();
                }

                int exit = process.waitFor();
                String output = readStreamIntoString(process.getInputStream());
                if (exit != 0) {
                    System.out.printf("Input:\n%s\nOutput:\n%s\n", input, output);
                    System.out.printf("Exit code: %s\n", exit);
                    System.out.printf("\n ");

                    System.exit(exit);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static List<String> getMuatedInputs(String seedInput) {
        List<String> inputs = new ArrayList<String>();

        inputs.add(mutateWithExcessiveLength(seedInput));

        for (int i = 0; i < 20; i++) {
            inputs.add(mutateWithSpecialChars(seedInput));
            inputs.add(mutateDeleteChars(seedInput));
            inputs.add(mutateWithMalformedHtml(seedInput));
            inputs.add(mutateWithInvalidChars(seedInput));
            inputs.add(mutateDuplicateChar(seedInput));
        }

        return inputs;
    }

    public static String mutateWithExcessiveLength(String input) {
        StringBuilder longString = new StringBuilder("A".repeat(100));
        return input.replace("value", longString);
    }

    public static String mutateWithSpecialChars(String input) {
        return input.repeat(100);
    }

    public static String mutateDeleteChars(String input) {
        Random random = new Random();
        StringBuilder mutated = new StringBuilder(input);
        int position = random.nextInt(input.length());
        int randomLen = random.nextInt(input.length() - position) + 1;
        mutated.delete(position, position + randomLen);
        return mutated.toString();
    }

    public static String mutateWithMalformedHtml(String input) {
        Random random = new Random();
        StringBuilder mutatedInput = new StringBuilder(input);
        int position = random.nextInt(input.length() + 1);
        return mutatedInput.insert(position, ">>".repeat(100)).toString();
    }

    public static String mutateWithInvalidChars(String input) {
        return input.replace("value", "\0\0\0");
    }

    public static  String mutateDuplicateChar(String input) {
        Random random = new Random();
        StringBuilder mutated = new StringBuilder(input);
        char charToDuplicate = mutated.charAt(random.nextInt(input.length()));
        String chars = String.valueOf(charToDuplicate).repeat(100);
        int position = random.nextInt(input.length());
        mutated.insert(position, chars);
        return mutated.toString();
    }

    private static String readStreamIntoString(InputStream inputStream) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        return reader.lines()
                .map(line -> line + System.lineSeparator())
                .collect(Collectors.joining());
    }

    private static List<String> getMutatedInputs(String seedInput, Collection<Function<String, String>> mutators) {
        return mutators.stream()
                .map(mutator -> mutator.apply(seedInput))
                .collect(Collectors.toList());
    }

    private static String switchCase(String input) {
        return input.chars()
                .mapToObj(c -> Character.isUpperCase(c) ? Character.toLowerCase((char) c) : Character.toUpperCase((char) c))
                .map(Object::toString)
                .collect(Collectors.joining());
    }
}
