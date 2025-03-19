package org.example

import dev.inmo.tgbotapi.bot.ktor.telegramBot
import dev.inmo.tgbotapi.extensions.api.edit.media.editMessageMedia
import dev.inmo.tgbotapi.extensions.api.files.downloadFile
import dev.inmo.tgbotapi.extensions.api.get.getFileAdditionalInfo
import dev.inmo.tgbotapi.extensions.api.send.replyWithDocument
import dev.inmo.tgbotapi.extensions.api.send.replyWithPhoto
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.buildBehaviourWithLongPolling
import dev.inmo.tgbotapi.extensions.utils.extensions.raw.text
import dev.inmo.tgbotapi.extensions.utils.types.buttons.replyKeyboard
import dev.inmo.tgbotapi.extensions.utils.types.buttons.simpleButton
import dev.inmo.tgbotapi.extensions.utils.withContentOrThrow
import dev.inmo.tgbotapi.requests.abstracts.asMultipartFile
import dev.inmo.tgbotapi.types.buttons.ReplyKeyboardRemove
import dev.inmo.tgbotapi.types.media.TelegramMediaPhoto
import dev.inmo.tgbotapi.types.message.abstracts.PrivateContentMessage
import dev.inmo.tgbotapi.types.message.content.PhotoContent
import dev.inmo.tgbotapi.utils.filenameFromUrl
import dev.inmo.tgbotapi.utils.row
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import me.y9san9.ksm.telegram.TelegramStorage
import me.y9san9.ksm.telegram.buildTelegramFSM
import me.y9san9.ksm.telegram.json.goto
import me.y9san9.ksm.telegram.json.json
import me.y9san9.ksm.telegram.json.receive
import me.y9san9.ksm.telegram.routing
import me.y9san9.ksm.telegram.routing.state
import me.y9san9.ksm.telegram.state.*
import me.y9san9.ksm.telegram.state.data.StateData
import me.y9san9.ksm.telegram.state.goto
import me.y9san9.ksm.telegram.storage
import org.example.utils.MessageService
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files.setAttribute
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.metadata.IIOMetadataNode
import javax.imageio.stream.FileImageOutputStream
import javax.imageio.stream.ImageOutputStream
import kotlin.math.pow
import kotlin.math.sqrt


suspend fun main() {
    val token: String = System.getenv("BOT_TOKEN")
    val bot = telegramBot(token)
    val messageService = MessageService()

    val fsm = buildTelegramFSM {
        json = Json {
            prettyPrint = true
        }
        storage = TelegramStorage.InMemory()
        routing {
            initial = "initial"
            state {
                name = "initial"
                handle {
                    goto("welcome")
                }       
            }
            
            state { 
                name = "welcome"
                transition {
                    bot.sendMessage(
                        chatId = user.id,
                        text = WELCOME,
                        replyMarkup = replyKeyboard(true) {
                            row {
                                simpleButton("Отправить изображение")
                            }
                        }
                    )
                }
                handle {
                    with(message){
                        if (text == "Отправить изображение") {
                            goto("receivePic")
                        } else {
                            bot.sendMessage(
                                chatId = chat.id,
                                text = COMMAND_FAILED
                            )
                            stay(this@state.name)
                        }
                    }
                }
            }
            state {
                name = "receivePic"
                transition {
                    bot.sendMessage(
                        chatId = user.id,
                        text = PIC,
                        replyMarkup = ReplyKeyboardRemove()
                    )
                }

                handle {
                    try {
                        val message = message.withContentOrThrow<PhotoContent>()
                        val uuid = user.id.toString()
                        messageService.addMessage(uuid, message)
                        goto("sendOrEdit", data = StateData.String(uuid))
                    } catch (_: Exception) {
                        bot.sendMessage(
                            user.id,
                            text = PIC_INPUT_ERROR
                        )
                        goto(this@state.name)
                    }
                }
            }

            state {
                name = "sendOrEdit"
                transition {
                    bot.sendMessage(
                        chatId = user.id,
                        text = "Выберите действие:",
                        replyMarkup = replyKeyboard(true) {
                            row {
                                simpleButton("Подтвердить")
                            }
                            row {
                                simpleButton("Отправить другое изображение")
                            }
                            row {
                                simpleButton("Выйти в меню")
                            }
                        }
                    )
                }

                handle {
                    val uuid = receive<String>()
                    if (message.text == "Подтвердить") {
                        goto("confirmEffect")
                    } else if (message.text == "Отправить другое изображение") {
                        messageService.removeMessage(uuid)
                        goto("receivePic")
                    } else if (message.text == "Выйти в меню") {
                        messageService.removeMessage(uuid)
                        goto("welcome")
                    } else {
                        bot.sendMessage(
                            chatId = user.id,
                            text = COMMAND_FAILED
                        )
                        stay(this@state.name)
                    }
                }
            }
            
            state { 
                name = "confirmEffect"
                transition {
                    bot.sendMessage(
                        chatId = user.id,
                        text = PIC_INPUT_SUCCESS,
                        replyMarkup = replyKeyboard(true) {
                            row {
                                simpleButton("Начать")
                            }
                            row { 
                                simpleButton("Начать с gif")
                            }
                            row {
                                simpleButton("Выйти в меню")
                            }
                        }
                    )
                }
                handle {
                    if (message.text == "Начать") {
                        goto("startEffect", data = StateData.String("withoutGif"))
                    } else if (message.text == "Начать с gif"){
                        goto("startEffect", data = StateData.String("withGif"))
                    }
                    else if (message.text == "Выйти в меню") {
                        goto("welcome")
                    } else {
                        bot.sendMessage(
                            chatId = user.id,
                            text = COMMAND_FAILED
                        )
                        stay(this@state.name)
                    }
                }
                
            }
            state {
                name = "startEffect"
                transition {
                    val mode = receive<String>()
                    val pic = messageService.getMessage(user.id.toString())
                    val pathedFile = bot.getFileAdditionalInfo(pic.content.media)
                    val byteArray = bot.downloadFile(pathedFile)
                    val outFile = File(pathedFile.fileName.filenameFromUrl)
                    FileOutputStream(outFile).write(byteArray)

                    val negativeFile = File("${pathedFile.fileId.fileId}_negative.jpeg")
                    val whiteBlackFile = File("${pathedFile.fileId.fileId}_whiteBlack.jpeg")
                    val gifFile = File("${pathedFile.fileId.fileId}_effect.gif")

                    val image = ImageIO.read(outFile)

                    val negativeImage = imageToNegative(image)
                    addBlackDot(negativeImage, 1.0)
                    ImageIO.write(negativeImage, "jpeg", negativeFile)

                    val whiteBlackImage = imageToWhiteBlack(image)
                    ImageIO.write(whiteBlackImage, "jpeg", whiteBlackFile)

                    when (mode) {
                        "withGif" -> {
                            bot.sendMessage(
                                user.id,
                                "Смотрите в черную точку по центру (рекомендуется открыть гифку):",
                                replyMarkup = ReplyKeyboardRemove()
                            )
                            createGif(negativeImage, 15, whiteBlackImage, 5, gifFile)
                            bot.replyWithDocument(message, gifFile.asMultipartFile())
                            gifFile.delete()
                        }
                        "withoutGif" -> {
                            bot.sendMessage(
                                user.id,
                                "Смотрите в черную точку по центру фото, не открывая его:",
                                replyMarkup = ReplyKeyboardRemove()
                            )
                            
                            val replayMessageId = bot.replyWithPhoto(
                                message,
                                negativeFile.asMultipartFile()
                            )
                            delay(15000)
                            bot.editMessageMedia(replayMessageId, TelegramMediaPhoto(whiteBlackFile.asMultipartFile()))
                        }
                    }

                    negativeFile.delete()
                    whiteBlackFile.delete()
                    outFile.delete()
                    messageService.removeMessage(user.id.toString())

                    delay(10000)
                    bot.sendMessage(
                        user.id,
                        "Хотите еще раз попробовать?",
                        replyMarkup = replyKeyboard(true) {
                            row {
                                simpleButton("Да")
                            }
                            row {
                                simpleButton("Нет, выйти в меню")
                            }
                        }
                    )
                }
                handle {
                    if (message.text == "Да") {
                        goto("receivePic")
                    } else if (message.text == "Нет, выйти в меню") {
                        goto("welcome")
                    } else {
                        bot.sendMessage(
                            user.id,
                            "Команда не распознана. Попробуйте еще раз."
                        )
                        stay(this@state.name)
                    }
                }
            }

        }
    }

    bot.buildBehaviourWithLongPolling {
        launch {
            val flow = messagesFlow.filter { it.data is PrivateContentMessage<*> }
            fsm.run(bot, flow)
        }
        launch {
            allUpdatesFlow.collect { update ->
                println(update)
            }
        }
    }.join()
}

suspend fun StateHandler.Scope.stay(name: String) {
    goto(name, transition = false)
}

fun addBlackDot(image: BufferedImage, radiusPercent: Double) {
    val centerX = image.width / 2
    val centerY = image.height / 2
    val radius = (radiusPercent / 100 * image.width).toInt().coerceAtLeast(1)

    for (i in centerX - radius..centerX + radius) {
        for (j in centerY - radius..centerY + radius) {
            if (i in 0 until image.width && j in 0 until image.height) {
                val distance = sqrt((i - centerX).toDouble().pow(2) + (j - centerY).toDouble().pow(2))
                if (distance <= radius) {
                    image.setRGB(i, j, 0) 
                }
            }
        }
    }
}

fun imageToNegative(image: BufferedImage): BufferedImage {
    val negativeImage = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)

    for (i in 0 until image.width) {
        for (j in 0 until image.height) {
            val rgb = image.getRGB(i, j)
            negativeImage.setRGB(i, j, (0xffffffff - rgb).toInt())
        }
    }

    return negativeImage
}

fun imageToWhiteBlack(image: BufferedImage): BufferedImage {
    val whiteBlackImage = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)

    for (i in 0 until image.width) {
        for (j in 0 until image.height) {
            val rgb = image.getRGB(i, j)

            val r = (rgb shr 16) and 0xFF
            val g = (rgb shr 8) and 0xFF
            val b = rgb and 0xFF

            val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()

            val grayRgb = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
            whiteBlackImage.setRGB(i, j, grayRgb)
        }
    }

    return whiteBlackImage
}

fun createGif(image1: BufferedImage, duration1: Int, image2: BufferedImage, duration2: Int, outputFile: File) {
    val writer = ImageIO.getImageWritersByFormatName("gif").next()
    val output: ImageOutputStream = FileImageOutputStream(outputFile)
    writer.output = output

    writer.prepareWriteSequence(null)

    fun addFrame(image: BufferedImage, duration: Int) {
        val typeSpecifier = ImageTypeSpecifier.createFromRenderedImage(image)
        val metadata = writer.getDefaultImageMetadata(typeSpecifier, null)
        val metaFormatName = metadata.nativeMetadataFormatName

        val root = IIOMetadataNode(metaFormatName)
        val graphicsControlExtensionNode = IIOMetadataNode("GraphicControlExtension").apply {
            setAttribute("disposalMethod", "none")
            setAttribute("userInputFlag", "FALSE")
            setAttribute("transparentColorFlag", "FALSE")
            setAttribute("delayTime", (duration * 100).toString()) 
            setAttribute("transparentColorIndex", "0")
        }

        val appExtensionsNode = IIOMetadataNode("ApplicationExtensions")
        val appExtension = IIOMetadataNode("ApplicationExtension").apply {
            setAttribute("applicationID", "NETSCAPE")
            setAttribute("authenticationCode", "2.0")
            userObject = byteArrayOf(0x1, 0, 0).apply { this[1] = (0 and 0xFF).toByte(); this[2] = (0 shr 8).toByte() }
        }
        appExtensionsNode.appendChild(appExtension)

        root.appendChild(graphicsControlExtensionNode)
        root.appendChild(appExtensionsNode)

        metadata.mergeTree(metaFormatName, root)

        writer.writeToSequence(javax.imageio.IIOImage(image, null, metadata), null)
    }

    addFrame(image1, duration1)
    addFrame(image2, duration2)

    writer.endWriteSequence()
    output.close()
}
