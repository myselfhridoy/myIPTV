package com.streambd.iptv.utils
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
fun main() {
    try {
        org.schabi.newpipe.extractor.NewPipe.init(YouTubeExtractor.getDownloader(), org.schabi.newpipe.extractor.localization.Localization.DEFAULT, org.schabi.newpipe.extractor.localization.ContentCountry.DEFAULT)
        val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=5qap5aO4i9A") // Lofi girl live
        println("HLS: " + info.hlsUrl)
    } catch(e: Exception) {
        e.printStackTrace()
    }
}
