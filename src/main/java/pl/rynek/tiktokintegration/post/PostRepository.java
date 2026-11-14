package pl.rynek.tiktokintegration.post;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<TiktokPost, String> {
}
