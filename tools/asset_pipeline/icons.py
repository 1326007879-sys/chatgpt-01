"""Upstream MIT icon crop/resize helper, isolated for Android builds."""
def prepare_icon_image(source_img, side: int = 256):
    """裁掉透明留白并让角色尽量填满正方形图标画布。"""
    from PIL import Image

    img = source_img.convert("RGBA")
    # VP9 透明边缘可能散落 alpha=1 的噪点，直接 getbbox 会误判为整张画布。
    mask = img.getchannel("A").point(lambda a: 255 if a > 8 else 0)
    bbox = mask.getbbox()
    if bbox is None:
        raise ValueError("帧全透明，无法生成图标")
    img = img.crop(bbox)

    char_w, char_h = img.size
    scale = min(0.977 * side / char_w, 0.977 * side / char_h)
    char = img.resize(
        (max(1, int(round(char_w * scale))), max(1, int(round(char_h * scale)))),
        Image.Resampling.LANCZOS,
    )
    canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    canvas.alpha_composite(char, ((side - char.width) // 2, (side - char.height) // 2))
    return canvas
