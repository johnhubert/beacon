package com.beacon.rest.officials.controller;

import java.util.List;

import com.beacon.rest.officials.model.OfficialDetail;
import com.beacon.rest.officials.model.OfficialSummary;
import com.beacon.rest.officials.service.OfficialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Exposes REST endpoints consumed by the mobile application to retrieve official data.
 */
@RestController
@RequestMapping("/api/officials")
@Tag(name = "Officials", description = "Roster and profile endpoints for public officials")
public class OfficialController {

    private final OfficialService officialService;

    public OfficialController(OfficialService officialService) {
        this.officialService = officialService;
    }

    @GetMapping
    @Operation(summary = "List public officials",
            description = "Returns a list of public officials ordered by the default MongoDB sort order.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Officials returned successfully",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = OfficialSummary.class))))
            })
    public List<OfficialSummary> listOfficials(
            @Parameter(description = "Maximum number of officials to return", example = "25")
            @RequestParam(value = "limit", required = false) Integer limit) {
        return officialService.fetchOfficials(limit);
    }

    @GetMapping("/{sourceId}")
    @Operation(summary = "Retrieve a public official",
            description = "Looks up a public official using the stable source identifier (for example, a Bioguide ID).",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Official located",
                            content = @Content(schema = @Schema(implementation = OfficialDetail.class))),
                    @ApiResponse(responseCode = "404", description = "Official not found", content = @Content)
            })
    public ResponseEntity<OfficialDetail> getOfficial(
            @Parameter(description = "Stable source identifier for the official", example = "A000360")
            @PathVariable("sourceId") String sourceId) {
        return officialService.findOfficial(sourceId)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Official not found"));
    }
}
