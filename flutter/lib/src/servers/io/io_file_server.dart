import 'dart:io';
import 'dart:typed_data';

import 'package:flutter/foundation.dart';
import 'package:path/path.dart';
import 'package:storage_inspector/src/servers/file_server.dart';
import 'package:storage_inspector/src/util/extensions.dart';
import 'package:uuid/uuid.dart';

/// File server that serves files using dart:io framework. By default all files
/// are returned without filtering.
///
/// NOTE: No security checks are performed on the input paths
class DefaultFileServer implements FileServer {
  final String _root;

  @override
  final String? icon = null;

  @override
  final String name;

  @override
  final String id = const Uuid().v4();

  DefaultFileServer(this._root, this.name);

  @override
  Future<List<FileInfo>> browse(String root) async {
    final newPath =
        join(_root, root.startsWith('/') ? root.substring(1) : root);
    final findRoot = FileSystemEntity.typeSync(newPath);
    if (findRoot != FileSystemEntityType.directory) {
      if (findRoot == FileSystemEntityType.file) {
        return [
          FileInfo(path: root, size: File(newPath).statSync().size),
        ];
      }
      return [FileInfo(path: root, size: 0)];
    }

    final dir = Directory(newPath);
    if (!dir.existsSync()) throw ArgumentError('Path "$root" does not exist');

    return dir.list(recursive: true).mapNotNull(
      (path) {
        final fullPath = relative(path.path, from: newPath);
        final stat = path.statSync();
        if (stat.type == FileSystemEntityType.file) {
          return FileInfo(path: fullPath, size: stat.size);
        } else {
          return null;
        }
      },
    ).then((r) => r.toList());
  }

  @override
  Future<void> delete(String path, {required bool recursive}) {
    final newPath = join(_root, path);
    File(newPath).deleteSync(recursive: recursive);
    return SynchronousFuture(null);
  }

  @override
  Future<Uint8List> read(String path) {
    final filePath = File(join(_root, path));
    if (!filePath.existsSync()) {
      throw ArgumentError('File "$path" does not exist');
    }
    return filePath.readAsBytes();
  }

  @override
  Future<void> write(String path, Uint8List data) {
    final filePath = File(join(_root, path));
    if (!filePath.parent.existsSync()) {
      filePath.parent.createSync(recursive: true);
    }
    return filePath.writeAsBytes(data);
  }
}
