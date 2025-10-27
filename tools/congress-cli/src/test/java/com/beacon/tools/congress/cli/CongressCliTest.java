package com.beacon.tools.congress.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CongressCliTest {

    @Test
    void tableRendererProducesAlignedColumns() {
        CongressCli.TableRenderer renderer = new CongressCli.TableRenderer(List.of("Header", "Value"));
        renderer.addRow(List.of("Alpha", "1"));
        renderer.addRow(List.of("Beta", "200"));

        String table = renderer.render();

        assertThat(table).contains("| Header | Value |");
        assertThat(table).contains("| Alpha  | 1     |");
        assertThat(table).contains("| Beta   | 200   |");
        assertThat(table.lines().findFirst().orElse(""))
                .contains("+");
    }
}
