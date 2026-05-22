package com.flashgif.media.transcode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Thin wrapper around the system {@code ffmpeg} and {@code ffprobe} binaries.
 * Each transcode call spawns a process and blocks until exit; the caller
 * (TranscodeWorker) is responsible for off-loading from request threads.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FFmpegRunner {

    private final ObjectMapper objectMapper;

    @Value("${flashgif.transcode.ffmpeg-bin:ffmpeg}")
    private String ffmpegBin;

    @Value("${flashgif.transcode.ffprobe-bin:ffprobe}")
    private String ffprobeBin;

    @Value("${flashgif.transcode.timeout-seconds:120}")
    private int timeoutSeconds;

    public ProbeResult probe(Path input) throws IOException, InterruptedException {
        String json = run(List.of(
                ffprobeBin, "-v", "error",
                "-print_format", "json",
                "-show_streams", "-show_format",
                input.toString()
        ));
        JsonNode root = objectMapper.readTree(json);
        JsonNode video = firstVideoStream(root.path("streams"));
        Integer width  = video != null && video.hasNonNull("width")  ? video.get("width").asInt()  : null;
        Integer height = video != null && video.hasNonNull("height") ? video.get("height").asInt() : null;
        Integer durationMs = null;
        if (root.path("format").hasNonNull("duration")) {
            double seconds = Double.parseDouble(root.path("format").get("duration").asText());
            durationMs = (int) Math.round(seconds * 1000);
        }
        return new ProbeResult(width, height, durationMs);
    }

    public void toMp4(Path input, Path output) throws IOException, InterruptedException {
        run(List.of(ffmpegBin, "-y", "-i", input.toString(),
                "-vcodec", "libx264",
                "-pix_fmt", "yuv420p",
                "-movflags", "+faststart",
                "-acodec", "aac",
                "-vf", "scale='min(720,iw)':-2",
                output.toString()));
    }

    public void toAnimatedWebp(Path input, Path output) throws IOException, InterruptedException {
        run(List.of(ffmpegBin, "-y", "-i", input.toString(),
                "-vcodec", "libwebp_anim",
                "-loop", "0",
                "-preset", "default",
                "-an",
                "-vsync", "0",
                output.toString()));
    }

    public void toGif(Path input, Path output) throws IOException, InterruptedException {
        // Two-pass palette generation for good colour quality. Capped at 480px wide.
        run(List.of(ffmpegBin, "-y", "-i", input.toString(),
                "-vf", "fps=15,scale=480:-1:flags=lanczos,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse",
                output.toString()));
    }

    public void toPosterJpeg(Path input, Path output) throws IOException, InterruptedException {
        run(List.of(ffmpegBin, "-y", "-i", input.toString(),
                "-vf", "select=eq(n\\,0)",
                "-vframes", "1",
                output.toString()));
    }

    private String run(List<String> command) throws IOException, InterruptedException {
        log.debug("Running: {}", String.join(" ", command));
        Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
        StringBuilder out = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) out.append(line).append('\n');
        }
        if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IOException("Process timed out after " + timeoutSeconds + "s: " + command.get(0));
        }
        if (p.exitValue() != 0) {
            throw new IOException("Process " + command.get(0) + " exited " + p.exitValue() + ": " + out);
        }
        return out.toString();
    }

    private static JsonNode firstVideoStream(JsonNode streams) {
        if (streams == null || !streams.isArray()) return null;
        for (JsonNode s : streams) {
            if ("video".equals(s.path("codec_type").asText())) return s;
        }
        return null;
    }

    public record ProbeResult(Integer width, Integer height, Integer durationMs) {}
}
