package org.album.service;

import static com.google.common.io.Files.*;
import static org.springframework.util.Base64Utils.encodeToString;

import java.io.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.album.AlbumConfiguration;
import org.album.exception.*;
import org.album.image.Image;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @since 1.0-SNAPSHOT
 */
@RequiredArgsConstructor
@Slf4j
@Service
public class AlbumCommonServiceImpl implements AlbumCommonService {

  private final AlbumConfiguration configuration;
  private final AlbumCrudService crudService;

  @Transactional(readOnly = true)
  @Override
  public Response<Resource> getResource(long id) {
    Image image = crudService.getImageById(id);

    Resource resource = getPathResource(image.getFilename());
    if (!resource.exists()) {
      throw new ResourceNotExistsException(image.getFilename());
    }

    return new Response<>(image, resource);
  }

  @Transactional(readOnly = true)
  @Override
  public Response<String> getBase64(long id) {
    Image image = crudService.getImageById(id);

    Resource resource = getPathResource(image.getFilename());
    if (!resource.exists()) {
      throw new ResourceNotExistsException(image.getFilename());
    }

    try (InputStream inputStream = resource.getInputStream()) {
      byte[] allBytes = inputStream.readAllBytes();

      return new Response<>(image, encodeToString(allBytes));
    } catch (IOException e) {
      throw new AlbumCommonServiceException(e);
    }
  }

  @Transactional
  @Override
  public Image create(String filename, String contentType, Resource resource) {
    checkSupportedContentType(contentType);
    try (InputStream inputStream = resource.getInputStream()) {
      byte[] allBytes = inputStream.readAllBytes();

      String parentDirs = configuration.getParentDirs();
      createParentDirs(new File(parentDirs));

      File file = !StringUtils.isBlank(parentDirs) ? new File(parentDirs + "/" + filename) : new File(filename);
      write(allBytes, file);

      return crudService.createImage(filename, contentType, allBytes.length);
    } catch (IOException e) {
      throw new AlbumCommonServiceException(e);
    }
  }

  @Transactional
  @Override
  public void delete(long id) {
    Image image = crudService.deleteImageById(id);

    boolean filenameIsUnused = crudService.countImagesByFilename(image.getFilename()) == 0;
    if (filenameIsUnused) {
      Resource resource = getPathResource(image.getFilename());
      if (resource.exists()) {
        try {

          File file = resource.getFile();
          if (!file.delete()) {
            log.error("can't delete file {}", file.getAbsoluteFile());
            throw new IOException();
          }

        } catch (IOException e) {
          throw new AlbumCommonServiceException(e);
        }
      }
    }
  }

  private PathResource getPathResource(String filename) {
    String parentDirs = configuration.getParentDirs();
    File file = !StringUtils.isBlank(parentDirs) ? new File(parentDirs + "/" + filename) : new File(filename);
    return new PathResource(file.getPath());
  }

  private void checkSupportedContentType(String contentType) {
    boolean isNotSupported = !StringUtils.equalsAny(contentType, configuration.getContentTypes());
    if (isNotSupported) {
      throw new NotSupportedContentTypeException(contentType);
    }
  }
}
