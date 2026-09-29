package com.tiendabackend.tiendabackend.usuario;

import com.tiendabackend.tiendabackend.common.exception.RecursoNoEncontradoException;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true)
    public List<UsuarioDTO> listarTodo() {
        return usuarioRepository.findAll().stream()
                .map(UsuarioDTO::desdeEntidad)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UsuarioDTO obtenerPorId(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario", id));
        return UsuarioDTO.desdeEntidad(usuario);
    }

    @Transactional
    public UsuarioDTO crearUsuario(UsuarioRequestDTO dto) {
        if (usuarioRepository.existsByUsername(dto.getUsername())) {
            throw new IllegalArgumentException("El username '" + dto.getUsername() + "' ya está registrado");
        }
        if (usuarioRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("El email '" + dto.getEmail() + "' ya está registrado");
        }

        Usuario usuario = new Usuario();
        usuario.setUsername(dto.getUsername());
        usuario.setNombre(dto.getNombre());
        usuario.setEmail(dto.getEmail());
        usuario.setTelefono(dto.getTelefono());
        usuario.setRol(dto.getRol());
        usuario.setActivo(true);
        usuario.setFechaCreacion(Instant.now());

        Usuario guardado = usuarioRepository.save(usuario);
        return UsuarioDTO.desdeEntidad(guardado);
    }

    @Transactional
    public UsuarioDTO actualizarUsuario(Long id, UsuarioRequestDTO dto) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario", id));

        if (!usuario.getUsername().equalsIgnoreCase(dto.getUsername()) && usuarioRepository.existsByUsername(dto.getUsername())) {
            throw new IllegalArgumentException("El username '" + dto.getUsername() + "' ya está en uso");
        }
        if (!usuario.getEmail().equalsIgnoreCase(dto.getEmail()) && usuarioRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("El email '" + dto.getEmail() + "' ya está en uso");
        }

        usuario.setUsername(dto.getUsername());
        usuario.setNombre(dto.getNombre());
        usuario.setEmail(dto.getEmail());
        usuario.setTelefono(dto.getTelefono());
        usuario.setRol(dto.getRol());

        Usuario guardado = usuarioRepository.save(usuario);
        return UsuarioDTO.desdeEntidad(guardado);
    }

    @Transactional
    public void eliminar(Long id) {
        if (!usuarioRepository.existsById(id)) {
            throw new RecursoNoEncontradoException("Usuario", id);
        }
        usuarioRepository.deleteById(id);
    }
}
