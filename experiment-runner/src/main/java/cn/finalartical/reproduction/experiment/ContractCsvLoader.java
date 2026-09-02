package cn.finalartical.reproduction.experiment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ContractCsvLoader {
    public List<ContractCase> load(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.isEmpty()) {
            return Collections.emptyList();
        }
        List<ContractCase> cases = new ArrayList<ContractCase>();
        for (int index = 1; index < lines.size(); index++) {
            String line = lines.get(index).trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] columns = line.split(",", -1);
            if (columns.length != 6) {
                throw new IllegalArgumentException("contract row " + (index + 1) + " must have 6 columns");
            }
            cases.add(new ContractCase(columns[0], columns[1], columns[2], columns[3], columns[4], columns[5]));
        }
        return Collections.unmodifiableList(cases);
    }
}
