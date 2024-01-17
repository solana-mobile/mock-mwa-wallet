package com.solana.mwallet.extensions

import android.widget.ImageView
import coil.ImageLoader
import coil.decode.SvgDecoder
import coil.request.ImageRequest

/*package*/ fun ImageView.loadImage(imgUrl: String?) {
    imgUrl?.let { url ->
        val imageLoader =
            ImageLoader.Builder(context)
                .components {
                    add(SvgDecoder.Factory())
                }.build()
        val request = ImageRequest.Builder(context).apply {
            data(url)
        }.target(onSuccess = { drawable ->
            setImageDrawable(drawable)
        }).build()

        imageLoader.enqueue(request)
    }
}