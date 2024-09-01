from PIL import Image
import requests
import uuid
import re
from bs4 import BeautifulSoup
import argparse
import os

def write_image_data(imgId, imgUrl):
    result = requests.get(imgUrl)
    filename = f'temp/{imgId}.png'
    with open(filename, 'wb+') as file:
        file.write(result.content)
    return filename

def save_to_pdf(filename, image_files: []):
    images = [
    Image.open(f).convert('RGB')
    for f in image_files
    ]

    pdf_path = f"result/{filename}"

    images[0].save(
        pdf_path, "PDF" ,resolution=100.0, save_all=True, append_images=images[1:]
    )
    print(filename)

def remove_temp_images(images: []):
    for image in images:
        os.remove(image)

#zenko
def write_zenko_data(imgId):
    imgUrl = f'https://zenko.online/api/proxy?url=https://zenko.b-cdn.net/{imgId}?optimizer=image&width=auto&quality=70&height=auto'
    return write_image_data(imgId, imgUrl)

def get_chapters_data(url: str):
    return requests.get(url).json()

def download_zenko_chapter(chapter_id: str):
    chapter = get_chapters_data(f'https://zenko-api.onrender.com/chapters/{chapter_id}')
    pages_data = chapter['pages']
    images = []
    for page in pages_data:
        images.append(write_zenko_data(page['imgUrl']))
    save_to_pdf(f"{chapter['name']}.pdf", images)
    remove_temp_images(images)

def download_zenko_title(title: str):
    chapters = get_chapters_data(f'https://zenko-api.onrender.com/titles/{title}/chapters')
    for chapter in chapters:
        download_zenko_chapter(chapter['id'])

def download_zenko_manga(manga_url: str):
    zenko_pattern= r'(https://)?zenko\.online/titles/(?P<title>\d+)(?P<chapter>(/\d+)?).*'
    match = re.search(zenko_pattern, manga_url)

    if(not match):
        return False
    title = match.group('title')
    try:
        chapter = match.group('chapter')[1:]
        download_zenko_chapter(chapter)
    except:
        download_zenko_title(title)

#manga in ua

def get_manga_in_ua_pages_data(chapter_url: str):
    soup = BeautifulSoup(requests.get(chapter_url).text, 'html.parser')
    page_imgs = soup.find_all("img")
    page_urls = []
    for page_img in page_imgs:
        page_urls.append(page_img['data-src'])
    return page_urls


def get_manga_in_ua_pdf(chapter_url: str, manga_name: str):
    page_urls = get_manga_in_ua_pages_data(chapter_url)
    pages = []
    for page_url in page_urls:
        page_id = str(uuid.uuid4())
        pages.append(write_image_data(page_id, page_url))
    file_name = manga_name + '.pdf'

    save_to_pdf(file_name, pages)
    remove_temp_images(pages)

def download_manga_in_ua(manga_url: str):
    BASE_CHAPTER_URI = 'https://manga.in.ua/engine/ajax/controller.php?mod=load_chapters_image'

    manga_in_ua_pattern = r'(https://)?manga\.in\.ua/chapters/(?P<chapter>\d+)-(?P<manga_name>.+).html'

    match = re.search(manga_in_ua_pattern, manga_url)
 
    if(not match):
        return False
    chapter_id = match.group('chapter')
    user_hash='3dc43ae45a8750d1cae8bc7f56386a1a8578517c'
    chapter_url = f'{BASE_CHAPTER_URI}&news_id={chapter_id}&action=show&user_hash={user_hash}'
    manga_name = match.group('manga_name')

    get_manga_in_ua_pdf(chapter_url, manga_name)

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('manga_url', help="Manga url to be downloaded. Currently works with zenko and manga in ua manga links", type=str)
    
    args = parser.parse_args()
    if not download_zenko_manga(args.manga_url):
        download_manga_in_ua(args.manga_url)

