package uk.gov.justice.digital.hmpps.learnerrecordsapi.config

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.logging.HttpLoggingInterceptor.Level
import org.slf4j.Logger
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit2.Retrofit
import retrofit2.converter.jaxb.JaxbConverterFactory
import uk.gov.justice.digital.hmpps.learnerrecordsapi.interfaces.LRSApiInterface
import uk.gov.justice.digital.hmpps.learnerrecordsapi.logging.LoggerUtil
import uk.gov.justice.digital.hmpps.learnerrecordsapi.logging.LoggerUtil.log
import java.util.concurrent.TimeUnit

@Configuration
class HttpClientConfiguration(private val lrsConfiguration: LRSConfiguration) {

  private val logger: Logger = LoggerUtil.getLogger<HttpClientConfiguration>()

  @Bean
  fun lrsClient(): LRSApiInterface = Retrofit.Builder()
    .baseUrl(lrsConfiguration.baseUrl)
    .client(sslHttpClient())
    .addConverterFactory(JaxbConverterFactory.create())
    .build().create(LRSApiInterface::class.java)

  fun sslHttpClient(): OkHttpClient {
    logger.log("Building HTTP client with SSL")
    val loggingInterceptor = HttpLoggingInterceptor()
    loggingInterceptor.level = Level.BASIC

    val sslContextConfiguration = SSLContextConfiguration(lrsConfiguration.pfxPath)
    val sslContext = sslContextConfiguration.createSSLContext()
    val trustManager = sslContextConfiguration.getTrustManager()

    val httpClientBuilder = OkHttpClient.Builder()
      .connectTimeout(lrsConfiguration.connectTimeout.toLong(), TimeUnit.SECONDS)
      .writeTimeout(lrsConfiguration.writeTimeout.toLong(), TimeUnit.SECONDS)
      .readTimeout(lrsConfiguration.readTimeout.toLong(), TimeUnit.SECONDS)
      .sslSocketFactory(sslContext.socketFactory, trustManager)
      .addInterceptor(loggingInterceptor)

    logger.log("HTTP client with SSL built successfully!")
    return httpClientBuilder.build()
  }
}
