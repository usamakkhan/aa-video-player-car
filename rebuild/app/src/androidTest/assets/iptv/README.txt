Synthetic test pattern generated locally with FFmpeg 7.1 (imageio-ffmpeg 0.6.0).
No third-party programme footage, streams or channel artwork.
clip.mp4: testsrc2=size=160x90:rate=10, 12 seconds, H.264/yuv420p, GOP 20, no audio.
stream.m3u8 and segment*.ts: remux of clip.mp4, HLS VOD, two-second segments.
These files exercise MP4 and segmented HLS transport/decoder behavior. They are not
evidence of real-provider compatibility, live-edge behavior or visual approval.
